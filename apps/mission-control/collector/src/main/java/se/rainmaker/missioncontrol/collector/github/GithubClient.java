package se.rainmaker.missioncontrol.collector.github;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only client for the GitHub REST API. It follows pagination, retries server errors and
 * timeouts with back-off, sits out short rate limits and turns every other failure into a {@link
 * SourceException} with a message that is safe to show. The token is only ever sent to {@code
 * apiUrl}.
 */
@Component
public class GithubClient {

  private static final Logger log = LoggerFactory.getLogger(GithubClient.class);
  private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");
  private static final DateTimeFormatter RESET_TIME =
      DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final GithubProperties properties;
  private final Clock clock;
  private final RestClient http;
  private final Retry retry;

  /** Builds the HTTP client with the timeouts and retry policy from the settings. */
  public GithubClient(GithubProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    requestFactory.setReadTimeout(properties.readTimeout());
    this.http =
        RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader(HttpHeaders.USER_AGENT, "rainmaker-mission-control")
            .build();
    IntervalFunction backoff = IntervalFunction.ofExponentialBackoff(properties.backoff(), 2.0);
    this.retry =
        Retry.of(
            "github",
            RetryConfig.<Page>custom()
                .maxAttempts(properties.maxAttempts())
                .retryExceptions(RetryableGithubException.class)
                .intervalBiFunction(
                    (attempt, result) ->
                        result.isLeft()
                                && result.getLeft() instanceof RetryableGithubException e
                                && e.waitMillis() != null
                            ? e.waitMillis()
                            : backoff.apply(attempt))
                .build());
  }

  /**
   * Reads every page of a GitHub list (e.g. {@code /repos/owner/name/issues}) and returns all
   * items.
   *
   * @throws SourceException when there is no token or GitHub cannot deliver
   */
  public List<JsonNode> getAll(String path, Map<String, String> query) {
    if (!properties.hasToken()) {
      throw new SourceException("No GitHub token: set MC_GITHUB_TOKEN");
    }
    UriComponentsBuilder first =
        UriComponentsBuilder.fromUri(properties.apiUrl()).path(path).queryParam("per_page", 100);
    query.forEach(first::queryParam);
    URI next = first.build().toUri();
    List<JsonNode> items = new ArrayList<>();
    for (int page = 1; next != null; page++) {
      if (page > properties.maxPages()) {
        throw new SourceException(
            "GitHub list " + path + " has more than " + properties.maxPages() + " pages");
      }
      Page result = fetchWithRetry(next, path);
      result.items().forEach(items::add);
      next = result.next();
    }
    return items;
  }

  private Page fetchWithRetry(URI uri, String path) {
    try {
      return retry.executeSupplier(() -> fetch(uri, path));
    } catch (RetryableGithubException e) {
      throw new SourceException(
          "GitHub did not answer after "
              + properties.maxAttempts()
              + " attempts (last: "
              + e.getMessage()
              + ")");
    }
  }

  private Page fetch(URI uri, String path) {
    try {
      return http.get()
          .uri(uri)
          .headers(headers -> headers.setBearerAuth(properties.token()))
          .exchange((request, response) -> toPage(response, path));
    } catch (ResourceAccessException e) {
      log.atWarn().addKeyValue("path", path).log("GitHub request timed out or failed to connect");
      throw new RetryableGithubException("no answer in time", null);
    }
  }

  private Page toPage(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, String path)
      throws IOException {
    int status = response.getStatusCode().value();
    HttpHeaders headers = response.getHeaders();
    if (status == 200) {
      String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonNode json = JSON.readTree(body);
      if (!json.isArray()) {
        throw new SourceException("GitHub answered " + path + " with something that is not a list");
      }
      return new Page(json.valueStream().toList(), nextLink(headers));
    }
    if (status == 401) {
      throw new SourceException("GitHub rejected the token (HTTP 401); check MC_GITHUB_TOKEN");
    }
    if (status == 403 || status == 429) {
      RuntimeException rateLimit = rateLimit(headers, status);
      if (rateLimit != null) {
        throw rateLimit;
      }
      throw new SourceException(
          "GitHub denied access (HTTP " + status + ") to " + path + "; check the token's access");
    }
    if (status == 404) {
      throw new SourceException("GitHub found nothing at " + path + " (HTTP 404)");
    }
    if (status == 301 || status == 307 || status == 308) {
      // Redirects are not followed, so the token never goes to another address.
      throw new SourceException(
          "GitHub says "
              + path
              + " has moved (HTTP "
              + status
              + "); the repository may be renamed, so update"
              + " mission-control.collector.github.repositories");
    }
    if (status >= 500) {
      log.atWarn()
          .addKeyValue("path", path)
          .addKeyValue("status", status)
          .log("GitHub server error");
      throw new RetryableGithubException("HTTP " + status, null);
    }
    throw new SourceException("GitHub answered " + path + " with HTTP " + status);
  }

  /**
   * A rate-limit answer: short waits are retried after the time GitHub asks for, long ones fail at
   * once with the reset time. Returns null when the answer is not about the rate limit.
   */
  private RuntimeException rateLimit(HttpHeaders headers, int status) {
    String retryAfter = headers.getFirst("Retry-After");
    String remaining = headers.getFirst("X-RateLimit-Remaining");
    String reset = headers.getFirst("X-RateLimit-Reset");
    Instant resetAt = null;
    if (retryAfter != null) {
      resetAt = retryAfterTime(retryAfter.strip());
    } else if ("0".equals(remaining) && reset != null && reset.strip().matches("\\d+")) {
      resetAt = Instant.ofEpochSecond(Long.parseLong(reset.strip()));
    }
    if (resetAt == null) {
      // Not a rate-limit answer we can read; handled like any other 403/429.
      return null;
    }
    long waitMillis = Math.max(0, resetAt.toEpochMilli() - clock.millis());
    if (waitMillis <= properties.rateLimitMaxWait().toMillis()) {
      log.atWarn().addKeyValue("waitMillis", waitMillis).log("GitHub rate limit; waiting");
      return new RetryableGithubException("rate limit, HTTP " + status, waitMillis);
    }
    return new SourceException(
        "GitHub rate limit reached; it resets at " + RESET_TIME.format(resetAt) + " UTC");
  }

  /**
   * When a {@code Retry-After} value says we may try again: seconds (e.g. {@code 30}) or an HTTP
   * date. Null for anything else, so a malformed header never crashes the run.
   */
  private Instant retryAfterTime(String value) {
    if (value.matches("\\d+")) {
      return clock.instant().plusSeconds(Long.parseLong(value));
    }
    try {
      return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException e) {
      log.atWarn().log("GitHub sent a Retry-After header that is not a number or a date");
      return null;
    }
  }

  /** The next page from the Link header, only when it is on the same host as {@code apiUrl}. */
  private URI nextLink(HttpHeaders headers) {
    String link = headers.getFirst(HttpHeaders.LINK);
    if (link == null) {
      return null;
    }
    Matcher matcher = NEXT_LINK.matcher(link);
    if (!matcher.find()) {
      return null;
    }
    URI next = URI.create(matcher.group(1));
    URI api = properties.apiUrl();
    if (!api.getScheme().equals(next.getScheme())
        || !api.getHost().equals(next.getHost())
        || api.getPort() != next.getPort()) {
      // The token must never be sent anywhere else.
      throw new SourceException("GitHub sent a pagination link to another host; stopped");
    }
    return next;
  }

  /** One page of a GitHub list. */
  private record Page(List<JsonNode> items, URI next) {}
}
