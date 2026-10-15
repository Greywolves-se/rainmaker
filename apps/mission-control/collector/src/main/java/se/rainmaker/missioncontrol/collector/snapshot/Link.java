package se.rainmaker.missioncontrol.collector.snapshot;

/** A link to an environment, dashboard or document. {@code url} is where it points. */
public record Link(String id, String title, Category category, String source, String url) {

  /** How links are grouped. */
  public enum Category {
    ENVIRONMENT,
    DASHBOARD,
    DOCUMENT,
    OTHER
  }
}
