package se.rainmaker.missioncontrol.collector.drive;

import com.google.auth.oauth2.GoogleCredentials;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only client for the Google Drive REST API (#113). It logs in with Google's standard login
 * ({@link GoogleLogin}, #120; no key is stored), limited to {@code drive.metadata.readonly}, so it
 * can never read content or write. It retries server errors and timeouts with back-off and turns
 * every other failure into a {@link SourceException} with a message that is safe to show. The
 * access token is never logged and is only sent to {@code apiUrl}.
 */
@Component
public class GoogleDriveClient {

  /** Read-only access to file metadata; no content, no changes. */
  static final String SCOPE = "https://www.googleapis.com/auth/drive.metadata.readonly";

  /** The newest files per folder; enough for "latest documents", so no paging is needed. */
  static final int MAX_FILES = 50;

  static final String FOLDER_TYPE = "application/vnd.google-apps.folder";
  static final String SHORTCUT_TYPE = "application/vnd.google-apps.shortcut";

  /** Only the metadata the source maps; nothing else is requested. */
  static final String FIELDS =
      "files(id,name,mimeType,createdTime,modifiedTime,webViewLink,lastModifyingUser(displayName))";

  /** Reasons Google gives with a 403 that mean "slow down", not "no access". */
  private static final Set<String> RATE_LIMIT_REASONS =
      Set.of("rateLimitExceeded", "userRateLimitExceeded");

  private static final Logger log = LoggerFactory.getLogger(GoogleDriveClient.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final GoogleDriveProperties properties;
  private final GoogleLogin login;
  private final RestClient http;
  private final Retry retry;
  private GoogleCredentials credentials;

  /**
   * Builds the HTTP client with the timeouts and retry policy from the settings; {@code login} is
   * Google's standard login in production (#120).
   */
  public GoogleDriveClient(GoogleDriveProperties properties, GoogleLogin login) {
    this.properties = properties;
    this.login = login;
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    requestFactory.setReadTimeout(properties.readTimeout());
    this.http =
        RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.USER_AGENT, "rainmaker-mission-control")
            .build();
    this.retry =
        Retry.of(
            "google-drive",
            RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .retryExceptions(RetryableDriveException.class)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.backoff(), 2.0))
                .build());
  }

  /**
   * The newest files directly in {@code folderId}, newest first: not trashed, no subfolders and no
   * shortcuts, at most {@link #MAX_FILES}. Shared drives work too.
   *
   * @throws SourceException when there is no Google login or Drive cannot deliver
   */
  public List<JsonNode> latestFiles(String folderId) {
    if (!GoogleDriveProperties.FOLDER_ID.matcher(folderId).matches()) {
      // Settings are checked at startup; this keeps the query safe if that ever changes.
      throw new SourceException("Google Drive folder id is not a valid Drive id");
    }
    URI uri = filesUri(folderId);
    JsonNode answer;
    try {
      answer = fetchWithRetry(uri, accessToken(false));
    } catch (TokenRejectedException e) {
      // The cached token was revoked or expired early: get a new one and try once more.
      log.atInfo().log("Google Drive rejected the access token; renewing it once");
      try {
        answer = fetchWithRetry(uri, accessToken(true));
      } catch (TokenRejectedException again) {
        throw new SourceException(
            "Google Drive rejected the access token (HTTP 401), also after renewing it");
      }
    }
    JsonNode files = answer.get("files");
    if (files == null || !files.isArray()) {
      throw new SourceException("Google Drive answered with something that is not a file list");
    }
    return files.valueStream().toList();
  }

  private URI filesUri(String folderId) {
    return UriComponentsBuilder.fromUri(properties.apiUrl())
        .path("/files")
        .queryParam(
            "q",
            "'"
                + folderId
                + "' in parents and trashed = false and mimeType != '"
                + FOLDER_TYPE
                + "' and mimeType != '"
                + SHORTCUT_TYPE
                + "'")
        .queryParam("fields", FIELDS)
        .queryParam("orderBy", "modifiedTime desc")
        .queryParam("pageSize", MAX_FILES)
        .queryParam("supportsAllDrives", true)
        .queryParam("includeItemsFromAllDrives", true)
        .encode()
        .build()
        .toUri();
  }

  /**
   * An access token; the library caches it and renews it shortly before it expires, or now when
   * {@code renew} is set. Synchronized, so projects collected in parallel never race on the cache.
   */
  private synchronized String accessToken(boolean renew) {
    if (credentials == null) {
      credentials = scopedLogin();
    }
    try {
      if (renew) {
        credentials.refresh();
      } else {
        credentials.refreshIfExpired();
      }
      return credentials.getAccessToken().getTokenValue();
    } catch (IOException e) {
      // The library's message can quote Google's answer; only the type is logged, never a token.
      log.atWarn()
          .addKeyValue("error", e.getClass().getSimpleName())
          .log("Google token request failed");
      throw new SourceException(
          "Google did not give an access token; check the Google login and that the service"
              + " account may be used");
    }
  }

  /** The login, limited to read-only metadata, so the token can never read content or write. */
  private GoogleCredentials scopedLogin() {
    try {
      return login.credentials().createScoped(List.of(SCOPE));
    } catch (IOException e) {
      // The library's message names files and variables it looked at; it is not shown.
      throw new SourceException(
          "No Google login: run google-github-actions/auth in CI, or gcloud auth"
              + " application-default login locally");
    }
  }

  private JsonNode fetchWithRetry(URI uri, String token) {
    try {
      return retry.executeSupplier(() -> fetch(uri, token));
    } catch (RetryableDriveException e) {
      throw new SourceException(
          "Google Drive did not answer after "
              + properties.maxAttempts()
              + " attempts (last: "
              + e.getMessage()
              + ")");
    }
  }

  private JsonNode fetch(URI uri, String token) {
    try {
      return http.get()
          .uri(uri)
          .headers(headers -> headers.setBearerAuth(token))
          .exchange((request, response) -> toJson(response));
    } catch (ResourceAccessException e) {
      log.atWarn().log("Google Drive request timed out or failed to connect");
      throw new RetryableDriveException("no answer in time");
    }
  }

  private static JsonNode toJson(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) throws IOException {
    int status = response.getStatusCode().value();
    if (status == 200) {
      return JSON.readTree(response.getBody());
    }
    if (status == 401) {
      throw new TokenRejectedException();
    }
    if (status == 403) {
      if (RATE_LIMIT_REASONS.contains(errorReason(response))) {
        log.atWarn().addKeyValue("status", status).log("Google Drive rate limit");
        throw new RetryableDriveException("rate limited (HTTP 403)");
      }
      throw new SourceException(
          "Google Drive denied access (HTTP 403); share the folder with the service account");
    }
    if (status == 404) {
      throw new SourceException(
          "Google Drive found no such folder (HTTP 404); check the folder id and that it is"
              + " shared with the service account");
    }
    if (status == 429 || status >= 500) {
      log.atWarn().addKeyValue("status", status).log("Google Drive busy or failing");
      throw new RetryableDriveException("HTTP " + status);
    }
    throw new SourceException("Google Drive answered with HTTP " + status);
  }

  /** Google's reason for an error, e.g. {@code rateLimitExceeded}; empty when there is none. */
  private static String errorReason(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
    try {
      return JSON.readTree(response.getBody()).at("/error/errors/0/reason").asString("");
    } catch (IOException | JacksonException e) {
      // No readable reason: treated as a plain 403.
      return "";
    }
  }

  /** A failure worth retrying: a timeout, a rate limit or a server error. */
  static final class RetryableDriveException extends RuntimeException {

    RetryableDriveException(String message) {
      super(message);
    }
  }

  /** HTTP 401: the access token is no longer accepted, so it is renewed once. */
  static final class TokenRejectedException extends RuntimeException {

    TokenRejectedException() {
      super("Google Drive rejected the access token (HTTP 401)");
    }
  }
}
