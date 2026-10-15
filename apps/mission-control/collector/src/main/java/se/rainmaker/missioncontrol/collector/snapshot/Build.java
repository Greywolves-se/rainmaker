package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;

/** A CI run. {@code finishedAt} is null while it is running. */
public record Build(
    String id,
    String workflow,
    String branch,
    Status status,
    Instant startedAt,
    Instant finishedAt,
    String source,
    String url) {

  /** Result of the run. */
  public enum Status {
    SUCCESS,
    FAILURE,
    RUNNING,
    CANCELLED
  }
}
