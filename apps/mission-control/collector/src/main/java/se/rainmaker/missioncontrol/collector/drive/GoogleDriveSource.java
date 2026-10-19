package se.rainmaker.missioncontrol.collector.drive;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.pipeline.SnapshotBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceCollector;
import se.rainmaker.missioncontrol.collector.snapshot.Document;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import tools.jackson.databind.JsonNode;

/**
 * The latest documents in each project's Google Drive folder (#113): metadata only (title, kind,
 * last editor, modified time, link), never their content. Documents changed in the last 7 days also
 * become events. A project without a folder in the settings gets no documents.
 *
 * <p>Only files directly in the folder are read, not those in subfolders, and only the newest
 * {@value GoogleDriveClient#MAX_FILES}.
 */
@Component
public class GoogleDriveSource implements SourceCollector {

  static final String ID = "google-drive";

  /** Documents changed within this time also appear in the recent-events feed. */
  static final Duration RECENT = Duration.ofDays(7);

  private static final Set<String> DOCUMENT_TYPES =
      Set.of(
          "application/vnd.google-apps.document",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/msword",
          "text/plain");
  private static final Set<String> SPREADSHEET_TYPES =
      Set.of(
          "application/vnd.google-apps.spreadsheet",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "application/vnd.ms-excel",
          "text/csv");
  private static final Set<String> PRESENTATION_TYPES =
      Set.of(
          "application/vnd.google-apps.presentation",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          "application/vnd.ms-powerpoint");

  private final GoogleDriveClient client;
  private final GoogleDriveProperties properties;
  private final Clock clock;

  /** Reads the folders in the settings with the given client. */
  public GoogleDriveSource(
      GoogleDriveClient client, GoogleDriveProperties properties, Clock clock) {
    this.client = client;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "Google Drive";
  }

  @Override
  public void collect(ProjectSettings project, SnapshotBuilder snapshot) {
    String folder = properties.folders().get(project.id());
    if (folder == null) {
      return;
    }
    Instant recent = clock.instant().minus(RECENT);
    for (JsonNode file : client.latestFiles(folder)) {
      Document document = document(file);
      snapshot.addDocument(document);
      if (document.modifiedAt().isAfter(recent)) {
        snapshot.addEvent(
            new Event(
                document.id() + "-modified",
                document.modifiedAt(),
                "Document updated: " + document.title(),
                ID,
                document.url()));
      }
    }
  }

  private static Document document(JsonNode file) {
    String id = file.get("id").asString();
    JsonNode link = file.get("webViewLink");
    return new Document(
        "gd-" + id,
        file.get("name").asString(),
        kind(file.get("mimeType").asString()),
        lastEditor(file.get("lastModifyingUser")),
        Instant.parse(file.get("modifiedTime").asString()),
        ID,
        link == null || link.isNull()
            ? "https://drive.google.com/file/d/" + id + "/view"
            : link.asString());
  }

  /** The kind of file, from its MIME type; anything unknown is {@code other}. */
  static Document.Kind kind(String mimeType) {
    if (DOCUMENT_TYPES.contains(mimeType)) {
      return Document.Kind.DOCUMENT;
    }
    if (SPREADSHEET_TYPES.contains(mimeType)) {
      return Document.Kind.SPREADSHEET;
    }
    if (PRESENTATION_TYPES.contains(mimeType)) {
      return Document.Kind.PRESENTATION;
    }
    return "application/pdf".equals(mimeType) ? Document.Kind.PDF : Document.Kind.OTHER;
  }

  private static String lastEditor(JsonNode user) {
    if (user == null || user.isNull()) {
      return null;
    }
    JsonNode name = user.get("displayName");
    return name == null || name.isNull() || name.asString().isBlank() ? null : name.asString();
  }
}
