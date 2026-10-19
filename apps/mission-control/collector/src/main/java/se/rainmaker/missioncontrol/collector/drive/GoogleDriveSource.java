package se.rainmaker.missioncontrol.collector.drive;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.pipeline.SnapshotBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceCollector;
import se.rainmaker.missioncontrol.collector.snapshot.Document;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import tools.jackson.databind.JsonNode;

/**
 * The latest documents in each project's Google Drive folder (#113): metadata only (title, kind,
 * last editor, modified time, link), never their content. Documents added or changed in the last 7
 * days also become events. A project without a folder in the settings is listed as disabled. Files
 * without a name are left out (and logged), since the contract needs a title.
 *
 * <p>Only files directly in the folder are read, not those in subfolders, and only the newest
 * {@value GoogleDriveClient#MAX_FILES}.
 */
@Component
public class GoogleDriveSource implements SourceCollector {

  static final String ID = "google-drive";

  /** Documents changed within this time also appear in the recent-events feed. */
  static final Duration RECENT = Duration.ofDays(7);

  private static final Logger log = LoggerFactory.getLogger(GoogleDriveSource.class);

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

  /** A project without a folder in the settings is not covered, so Drive shows as disabled. */
  @Override
  public Optional<String> notSetUpFor(ProjectSettings project) {
    return properties.folders().containsKey(project.id())
        ? Optional.empty()
        : Optional.of("No Google Drive folder for this project");
  }

  @Override
  public void collect(ProjectSettings project, SnapshotBuilder snapshot) {
    Instant recent = clock.instant().minus(RECENT);
    int unnamed = 0;
    for (JsonNode file : client.latestFiles(properties.folders().get(project.id()))) {
      if (text(file, "name") == null) {
        // The contract needs a title; one unnamed file must not fail the whole source.
        unnamed++;
        continue;
      }
      Document document = document(file);
      snapshot.addDocument(document);
      if (document.modifiedAt().isAfter(recent)) {
        snapshot.addEvent(event(document, createdAt(file), recent));
      }
    }
    if (unnamed > 0) {
      log.atWarn()
          .addKeyValue("project", project.id())
          .addKeyValue("files", unnamed)
          .log("Google Drive files without a name were left out");
    }
  }

  /** "Document added" when it was created in the recent window, else "Document updated". */
  private static Event event(Document document, Instant createdAt, Instant recent) {
    boolean added = createdAt != null && createdAt.isAfter(recent);
    return new Event(
        document.id() + (added ? "-added" : "-modified"),
        document.modifiedAt(),
        (added ? "Document added: " : "Document updated: ") + document.title(),
        ID,
        document.url());
  }

  private static Instant createdAt(JsonNode file) {
    String created = text(file, "createdTime");
    return created == null ? null : Instant.parse(created);
  }

  /** A text field, or null when it is missing, null or blank. */
  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() || value.asString().isBlank() ? null : value.asString();
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
    return user == null || user.isNull() ? null : text(user, "displayName");
  }
}
