package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.LocalDate;

/** A question for the owners or the team. {@code neededBy} is null when there is no date. */
public record Question(
    String id,
    String title,
    String owner,
    LocalDate neededBy,
    Status status,
    boolean critical,
    String source,
    String url) {

  /** Whether the question is answered. */
  public enum Status {
    OPEN,
    ANSWERED
  }
}
