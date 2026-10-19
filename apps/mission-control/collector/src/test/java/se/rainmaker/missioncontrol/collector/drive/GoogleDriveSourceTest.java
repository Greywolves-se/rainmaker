package se.rainmaker.missioncontrol.collector.drive;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.snapshot.Document;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class GoogleDriveSourceTest {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String TOKEN = "ya29.test-access-token";
  private static final String FOLDER = "folder-rainmaker";
  private static final ProjectSettings RAINMAKER =
      new ProjectSettings("rainmaker", "Rainmaker", null, "mission-control");
  private static final ProjectSettings MISSION_CONTROL =
      new ProjectSettings("mission-control", "Mission Control", "mission-control", null);

  @RegisterExtension
  static WireMockExtension google =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static String key;

  @BeforeAll
  static void createServiceAccountKey() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    String privateKey =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder()
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    Map<String, String> json = new LinkedHashMap<>();
    json.put("type", "service_account");
    json.put("project_id", "test-project");
    json.put("private_key_id", "test-key");
    json.put("private_key", privateKey);
    json.put("client_email", "mission-control@test-project.iam.gserviceaccount.com");
    json.put("client_id", "1");
    json.put("token_uri", google.baseUrl() + "/token");
    key = JSON.writeValueAsString(json);
  }

  @Test
  void mapsFilesToDocumentsAndRecentChangesToEvents() {
    stubToken();
    stubFiles(
        FOLDER,
        List.of(
            file(
                "doc1",
                "Pilot agreement",
                "application/vnd.google-apps.document",
                "2026-10-17T10:00:00Z",
                "Kim Ek",
                true),
            file(
                "sheet1",
                "Test report",
                "application/vnd.google-apps.spreadsheet",
                "2026-09-30T10:00:00Z",
                null,
                true),
            file(
                "pdf1",
                "Signed contract",
                "application/pdf",
                "2026-10-01T10:00:00Z",
                "Alex Berg",
                false),
            created(
                file(
                    "new1",
                    "Meeting notes",
                    "application/vnd.google-apps.document",
                    "2026-10-18T09:00:00Z",
                    "Kim Ek",
                    true),
                "2026-10-18T08:00:00Z")));

    Snapshot snapshot = run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount());

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.documents())
        .extracting(
            Document::id, Document::title, Document::kind, Document::lastEditor, Document::url)
        .containsExactly(
            tuple(
                "gd-doc1",
                "Pilot agreement",
                Document.Kind.DOCUMENT,
                "Kim Ek",
                "https://docs.google.com/d/doc1"),
            tuple(
                "gd-sheet1",
                "Test report",
                Document.Kind.SPREADSHEET,
                null,
                "https://docs.google.com/d/sheet1"),
            tuple(
                "gd-pdf1",
                "Signed contract",
                Document.Kind.PDF,
                "Alex Berg",
                "https://drive.google.com/file/d/pdf1/view"),
            tuple(
                "gd-new1",
                "Meeting notes",
                Document.Kind.DOCUMENT,
                "Kim Ek",
                "https://docs.google.com/d/new1"));
    assertThat(snapshot.events())
        .extracting(Event::id, Event::title)
        .containsExactly(
            tuple("gd-doc1-modified", "Document updated: Pilot agreement"),
            tuple("gd-new1-added", "Document added: Meeting notes"));
    google.verify(
        getRequestedFor(urlPathEqualTo("/drive/v3/files"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .withQueryParam("q", equalTo(query(FOLDER)))
            .withQueryParam("orderBy", equalTo("modifiedTime desc"))
            .withQueryParam("pageSize", equalTo("50"))
            .withQueryParam("supportsAllDrives", equalTo("true"))
            .withQueryParam("includeItemsFromAllDrives", equalTo("true")));
  }

  /** Every field the source maps must be requested; Drive leaves out any field not asked for. */
  @ParameterizedTest
  @CsvSource({
    "id",
    "name",
    "mimeType",
    "createdTime",
    "modifiedTime",
    "webViewLink",
    "lastModifyingUser(displayName)"
  })
  void requestsEveryFieldTheSourceMaps(String field) {
    stubToken();
    stubFiles(FOLDER, List.of());

    run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount());

    google.verify(
        getRequestedFor(urlPathEqualTo("/drive/v3/files"))
            .withQueryParam("fields", containing(field)));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "application/vnd.google-apps.document, DOCUMENT",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document, DOCUMENT",
    "text/csv, SPREADSHEET",
    "application/vnd.ms-excel, SPREADSHEET",
    "application/vnd.google-apps.presentation, PRESENTATION",
    "application/pdf, PDF",
    "image/png, OTHER"
  })
  void kindComesFromTheFileType(String mimeType, Document.Kind expected) {
    assertThat(GoogleDriveSource.kind(mimeType)).isEqualTo(expected);
  }

  @Test
  void eachProjectReadsOnlyItsOwnFolder() {
    stubToken();
    stubFiles(
        FOLDER,
        List.of(
            file("doc1", "Rainmaker doc", "application/pdf", "2026-10-18T10:00:00Z", null, true)));
    stubFiles(
        "folder-mc",
        List.of(
            file(
                "doc2",
                "Mission Control doc",
                "application/pdf",
                "2026-10-18T10:00:00Z",
                null,
                true)));
    Map<String, String> folders = Map.of("rainmaker", FOLDER, "mission-control", "folder-mc");

    assertThat(run(folders, RAINMAKER, serviceAccount()).documents())
        .extracting(Document::id)
        .containsExactly("gd-doc1");
    assertThat(run(folders, MISSION_CONTROL, serviceAccount()).documents())
        .extracting(Document::id)
        .containsExactly("gd-doc2");
  }

  @Test
  void projectWithoutFolderShowsDriveAsDisabledAndMakesNoCalls() {
    Snapshot snapshot = run(Map.of("rainmaker", FOLDER), MISSION_CONTROL, noLogin());

    assertThat(snapshot.sources().getFirst())
        .extracting(Source::status, Source::message)
        .containsExactly(Source.Status.DISABLED, "No Google Drive folder for this project");
    assertThat(snapshot.documents()).isEmpty();
    assertThat(google.getAllServeEvents()).isEmpty();
  }

  @Test
  void fileWithoutNameIsLeftOutAndTheRestIsKept(CapturedOutput output) {
    stubToken();
    stubFiles(
        FOLDER,
        List.of(
            file("blank", " ", "application/pdf", "2026-10-18T10:00:00Z", null, true),
            file(
                "doc1", "Pilot agreement", "application/pdf", "2026-10-18T10:00:00Z", null, true)));

    Snapshot snapshot = run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount());

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.documents()).extracting(Document::id).containsExactly("gd-doc1");
    assertThat(output.getOut()).contains("Google Drive files without a name were left out");
  }

  @Test
  void rejectedTokenIsRenewedOnceAndTheRequestRepeated() {
    stubToken();
    google.stubFor(
        get(urlPathEqualTo("/drive/v3/files"))
            .inScenario("token")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(401))
            .willSetStateTo("renewed"));
    google.stubFor(
        get(urlPathEqualTo("/drive/v3/files"))
            .inScenario("token")
            .whenScenarioStateIs("renewed")
            .willReturn(
                okJson(
                    JSON.writeValueAsString(
                        Map.of(
                            "files",
                            List.of(
                                file(
                                    "doc1",
                                    "Pilot agreement",
                                    "application/pdf",
                                    "2026-10-18T10:00:00Z",
                                    null,
                                    true)))))));

    Snapshot snapshot = run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount());

    assertThat(snapshot.documents()).extracting(Document::id).containsExactly("gd-doc1");
    google.verify(2, postRequestedFor(urlPathEqualTo("/token")));
  }

  @Test
  void tokenRejectedAgainAfterRenewingGivesError() {
    stubToken();
    google.stubFor(get(urlPathEqualTo("/drive/v3/files")).willReturn(aResponse().withStatus(401)));

    Source result =
        run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount()).sources().getFirst();

    assertThat(result.message())
        .isEqualTo("Google Drive rejected the access token (HTTP 401), also after renewing it");
    google.verify(2, getRequestedFor(urlPathEqualTo("/drive/v3/files")));
  }

  @ParameterizedTest(name = "HTTP {0} {1}")
  @CsvSource({
    "403, rateLimitExceeded, rate limited (HTTP 403)",
    "403, userRateLimitExceeded, rate limited (HTTP 403)",
    "429, '', HTTP 429"
  })
  void rateLimitsAreRetriedThenReported(int status, String reason, String last) {
    stubToken();
    google.stubFor(
        get(urlPathEqualTo("/drive/v3/files"))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withBody("{\"error\": {\"errors\": [{\"reason\": \"" + reason + "\"}]}}")));

    Source result =
        run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount()).sources().getFirst();

    assertThat(result.message())
        .isEqualTo("Google Drive did not answer after 2 attempts (last: " + last + ")");
    google.verify(2, getRequestedFor(urlPathEqualTo("/drive/v3/files")));
  }

  @Test
  void unknownFolderGivesHint() {
    stubToken();
    google.stubFor(get(urlPathEqualTo("/drive/v3/files")).willReturn(aResponse().withStatus(404)));

    Source result =
        run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount()).sources().getFirst();

    assertThat(result.message())
        .contains("HTTP 404")
        .contains("check the folder id and that it is shared with the service account");
  }

  @Test
  void slowAnswersAreRetriedThenReported() {
    stubToken();
    google.stubFor(
        get(urlPathEqualTo("/drive/v3/files"))
            .willReturn(okJson("{\"files\": []}").withFixedDelay(2000)));

    Source result =
        run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount(), Duration.ofMillis(300))
            .sources()
            .getFirst();

    assertThat(result.message())
        .isEqualTo("Google Drive did not answer after 2 attempts (last: no answer in time)");
    google.verify(2, getRequestedFor(urlPathEqualTo("/drive/v3/files")));
  }

  @Test
  void withoutGoogleLoginTheSourceIsAnErrorWithClearMessage(CapturedOutput output) {
    Source result = run(Map.of("rainmaker", FOLDER), RAINMAKER, noLogin()).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message())
        .isEqualTo(
            "No Google login: run google-github-actions/auth in CI, or gcloud auth"
                + " application-default login --impersonate-service-account=<e-mail> locally");
    // The library's own message names local files; it is never shown or logged.
    assertThat(result.message()).doesNotContain("private-path");
    assertThat(output.getAll()).doesNotContain("private-path");
    assertThat(google.getAllServeEvents()).isEmpty();
  }

  @Test
  void malformedLoginFileGivesSafeError(CapturedOutput output) {
    GoogleLogin malformed =
        () -> {
          throw new IllegalArgumentException("Bad file /home/someone/private-path/adc.json");
        };

    Source result = run(Map.of("rainmaker", FOLDER), RAINMAKER, malformed).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).startsWith("No Google login");
    assertThat(output.getAll()).doesNotContain("private-path");
  }

  /**
   * A plain gcloud user login ignores the read-only scope and would act with the user's own Drive
   * access, so it is refused before any call to Google.
   */
  @Test
  void plainUserLoginIsRefusedBeforeAnyCall(CapturedOutput output) {
    GoogleLogin userLogin =
        () ->
            UserCredentials.newBuilder()
                .setClientId("client-id")
                .setClientSecret("client-secret")
                .setRefreshToken("personal-refresh-token")
                .build();

    Source result = run(Map.of("rainmaker", FOLDER), RAINMAKER, userLogin).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message())
        .isEqualTo(
            "This Google login cannot be limited to read-only Drive metadata: log in with gcloud"
                + " auth application-default login --impersonate-service-account=<e-mail>");
    assertThat(google.getAllServeEvents()).isEmpty();
    assertThat(output.getAll()).doesNotContain("personal-refresh-token");
  }

  @Test
  void impersonatedLoginCanBeLimitedToReadOnly() throws IOException {
    GoogleCredentials impersonated =
        ImpersonatedCredentials.newBuilder()
            .setSourceCredentials(serviceAccount().credentials())
            .setTargetPrincipal("mission-control-drive-reader@test-project.iam.gserviceaccount.com")
            .setScopes(List.of())
            .build();

    assertThat(GoogleDriveClient.canBeLimited(impersonated)).isTrue();
    assertThat(GoogleDriveClient.canBeLimited(serviceAccount().credentials())).isTrue();
  }

  @Test
  void rejectedLoginGivesSafeError(CapturedOutput output) {
    google.stubFor(
        post(urlPathEqualTo("/token"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody(
                        "{\"error\": \"invalid_grant\", \"detail\": \"answer-from-google\"}")));

    Source result =
        run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount()).sources().getFirst();

    assertThat(result.message()).contains("did not give an access token");
    // Google's answer can describe the login; only the exception type is logged.
    assertThat(result.message()).doesNotContain("answer-from-google");
    assertThat(output.getAll()).doesNotContain("answer-from-google");
  }

  @Test
  void folderNotSharedGivesHint() {
    stubToken();
    google.stubFor(get(urlPathEqualTo("/drive/v3/files")).willReturn(aResponse().withStatus(403)));

    Source result =
        run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount()).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message())
        .contains("HTTP 403")
        .contains("share the folder with the service account");
  }

  @Test
  void serverErrorsAreRetriedThenReported(CapturedOutput output) {
    stubToken();
    google.stubFor(get(urlPathEqualTo("/drive/v3/files")).willReturn(aResponse().withStatus(503)));

    Source result =
        run(Map.of("rainmaker", FOLDER), RAINMAKER, serviceAccount()).sources().getFirst();

    assertThat(result.message())
        .isEqualTo("Google Drive did not answer after 2 attempts (last: HTTP 503)");
    google.verify(2, getRequestedFor(urlPathEqualTo("/drive/v3/files")));
    assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("BEGIN PRIVATE KEY");
  }

  /**
   * Logs in as the generated test service account, whose token address is WireMock. Never Google's
   * standard login: no test may use a real login or call Google.
   */
  private static GoogleLogin serviceAccount() {
    return () ->
        ServiceAccountCredentials.fromStream(
            new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));
  }

  /** No login available, like the standard login on a machine without gcloud or CI auth. */
  private static GoogleLogin noLogin() {
    return () -> {
      throw new IOException("No credentials found in /home/someone/private-path/adc.json");
    };
  }

  private static Snapshot run(
      Map<String, String> folders, ProjectSettings project, GoogleLogin login) {
    return run(folders, project, login, Duration.ofSeconds(5));
  }

  /** A short {@code readTimeout} only in the timeout test: a cold first request can be slow. */
  private static Snapshot run(
      Map<String, String> folders,
      ProjectSettings project,
      GoogleLogin login,
      Duration readTimeout) {
    GoogleDriveProperties properties =
        new GoogleDriveProperties(
            URI.create(google.baseUrl() + "/drive/v3"),
            folders,
            Duration.ofSeconds(1),
            readTimeout,
            2,
            Duration.ofMillis(1));
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    GoogleDriveSource source =
        new GoogleDriveSource(new GoogleDriveClient(properties, login), properties, clock);
    CollectorProperties collector =
        new CollectorProperties(List.of(project), Path.of("unused"), Map.of());
    return new CollectorPipeline(List.of(source), collector, new SnapshotValidator(), clock)
        .run(project);
  }

  /**
   * The Drive search the client must send for {@code folder}: its files, no trash, no subfolders
   * and no shortcuts. Written out here, so a change to the client's query fails the tests.
   */
  private static String query(String folder) {
    return "'"
        + folder
        + "' in parents and trashed = false"
        + " and mimeType != 'application/vnd.google-apps.folder'"
        + " and mimeType != 'application/vnd.google-apps.shortcut'";
  }

  private static Map<String, Object> created(Map<String, Object> file, String createdTime) {
    file.put("createdTime", createdTime);
    return file;
  }

  private static void stubToken() {
    google.stubFor(
        post(urlPathEqualTo("/token"))
            .willReturn(
                okJson(
                    "{\"access_token\": \""
                        + TOKEN
                        + "\", \"expires_in\": 3600, \"token_type\": \"Bearer\"}")));
  }

  private static void stubFiles(String folder, List<Map<String, Object>> files) {
    google.stubFor(
        get(urlPathEqualTo("/drive/v3/files"))
            .withQueryParam("q", equalTo(query(folder)))
            .willReturn(okJson(JSON.writeValueAsString(Map.of("files", files)))));
  }

  private static Map<String, Object> file(
      String id,
      String name,
      String mimeType,
      String modifiedTime,
      String editor,
      boolean withLink) {
    Map<String, Object> file = new LinkedHashMap<>();
    file.put("id", id);
    file.put("name", name);
    file.put("mimeType", mimeType);
    file.put("modifiedTime", modifiedTime);
    if (withLink) {
      file.put("webViewLink", "https://docs.google.com/d/" + id);
    }
    if (editor != null) {
      file.put("lastModifyingUser", Map.of("displayName", editor));
    }
    return file;
  }
}
