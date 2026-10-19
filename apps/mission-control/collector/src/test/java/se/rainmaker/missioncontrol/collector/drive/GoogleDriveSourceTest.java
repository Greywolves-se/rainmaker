package se.rainmaker.missioncontrol.collector.drive;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
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
                false)));

    Snapshot snapshot = run(Map.of("rainmaker", FOLDER), RAINMAKER, key);

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
                "https://drive.google.com/file/d/pdf1/view"));
    assertThat(snapshot.events())
        .extracting(Event::id, Event::title)
        .containsExactly(tuple("gd-doc1-modified", "Document updated: Pilot agreement"));
    google.verify(
        getRequestedFor(urlPathEqualTo("/drive/v3/files"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .withQueryParam("q", equalTo(query(FOLDER)))
            .withQueryParam("orderBy", equalTo("modifiedTime desc"))
            .withQueryParam("supportsAllDrives", equalTo("true"))
            .withQueryParam("includeItemsFromAllDrives", equalTo("true")));
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

    assertThat(run(folders, RAINMAKER, key).documents())
        .extracting(Document::id)
        .containsExactly("gd-doc1");
    assertThat(run(folders, MISSION_CONTROL, key).documents())
        .extracting(Document::id)
        .containsExactly("gd-doc2");
  }

  @Test
  void projectWithoutFolderGetsNothingAndMakesNoCalls() {
    Snapshot snapshot = run(Map.of("rainmaker", FOLDER), MISSION_CONTROL, "");

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.documents()).isEmpty();
    assertThat(google.getAllServeEvents()).isEmpty();
  }

  @Test
  void withoutKeyTheSourceIsAnErrorWithClearMessage() {
    Source result = run(Map.of("rainmaker", FOLDER), RAINMAKER, " ").sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).isEqualTo("No Google Drive key: set MC_GOOGLE_DRIVE_CREDENTIALS");
  }

  @Test
  void keyOfWrongShapeGivesSafeError(CapturedOutput output) {
    String broken = "{\"type\": \"service_account\", \"private_key\": \"secret-key-material\"}";

    Source result = run(Map.of("rainmaker", FOLDER), RAINMAKER, broken).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message())
        .isEqualTo("MC_GOOGLE_DRIVE_CREDENTIALS is not a valid Google service-account key");
    assertThat(output.getAll()).doesNotContain("secret-key-material");
  }

  @Test
  void rejectedKeyGivesSafeError(CapturedOutput output) {
    google.stubFor(
        post(urlPathEqualTo("/token"))
            .willReturn(aResponse().withStatus(400).withBody("{\"error\": \"invalid_grant\"}")));

    Source result = run(Map.of("rainmaker", FOLDER), RAINMAKER, key).sources().getFirst();

    assertThat(result.message()).contains("did not give an access token");
    assertThat(output.getAll()).doesNotContain("BEGIN PRIVATE KEY");
  }

  @Test
  void folderNotSharedGivesHint() {
    stubToken();
    google.stubFor(get(urlPathEqualTo("/drive/v3/files")).willReturn(aResponse().withStatus(403)));

    Source result = run(Map.of("rainmaker", FOLDER), RAINMAKER, key).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message())
        .contains("HTTP 403")
        .contains("share the folder with the service account");
  }

  @Test
  void serverErrorsAreRetriedThenReported(CapturedOutput output) {
    stubToken();
    google.stubFor(get(urlPathEqualTo("/drive/v3/files")).willReturn(aResponse().withStatus(503)));

    Source result = run(Map.of("rainmaker", FOLDER), RAINMAKER, key).sources().getFirst();

    assertThat(result.message())
        .isEqualTo("Google Drive did not answer after 2 attempts (last: HTTP 503)");
    google.verify(2, getRequestedFor(urlPathEqualTo("/drive/v3/files")));
    assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("BEGIN PRIVATE KEY");
  }

  private static Snapshot run(
      Map<String, String> folders, ProjectSettings project, String credentials) {
    GoogleDriveProperties properties =
        new GoogleDriveProperties(
            URI.create(google.baseUrl() + "/drive/v3"),
            credentials,
            folders,
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            2,
            Duration.ofMillis(1));
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    GoogleDriveSource source =
        new GoogleDriveSource(new GoogleDriveClient(properties), properties, clock);
    CollectorProperties collector =
        new CollectorProperties(List.of(project), Path.of("unused"), Map.of());
    return new CollectorPipeline(List.of(source), collector, new SnapshotValidator(), clock)
        .run(project);
  }

  /**
   * The Drive search the client must send for {@code folder}: its files, no trash, no subfolders.
   */
  private static String query(String folder) {
    return "'"
        + folder
        + "' in parents and trashed = false and mimeType != '"
        + GoogleDriveClient.FOLDER_TYPE
        + "'";
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
