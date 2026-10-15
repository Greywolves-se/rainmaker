package se.rainmaker.missioncontrol.collector.snapshot;

/** A project risk. */
public record Risk(
    String id,
    String title,
    Level level,
    String owner,
    String mitigation,
    String source,
    String url) {

  /** How serious the risk is. */
  public enum Level {
    GREEN,
    YELLOW,
    RED
  }
}
