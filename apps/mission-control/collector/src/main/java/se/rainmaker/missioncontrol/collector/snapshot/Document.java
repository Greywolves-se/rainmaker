package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;

/**
 * A document in a document store such as Google Drive (#113): metadata only, never its content.
 * {@code lastEditor} is null when the source does not say who changed it last.
 */
public record Document(
    String id,
    String title,
    Kind kind,
    String lastEditor,
    Instant modifiedAt,
    String source,
    String url) {

  /** What kind of file it is; the web app picks the label and icon. */
  public enum Kind {
    DOCUMENT,
    SPREADSHEET,
    PRESENTATION,
    PDF,
    OTHER
  }
}
