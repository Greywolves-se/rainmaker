package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.LocalDate;

/** An entry in the decision log. {@code reference} is null when there is none. */
public record Decision(
    String id,
    String title,
    String owner,
    LocalDate date,
    Status status,
    boolean needsOwnerDecision,
    String reference,
    String source,
    String url) {

  /** Where the decision stands. */
  public enum Status {
    PROPOSED,
    DECIDED,
    SUPERSEDED
  }
}
