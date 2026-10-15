package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;

/**
 * A data source and the result of this run. {@code fetchedAt} is null if it never succeeded; {@code
 * message} is null when the status is ok. The message never contains secrets.
 */
public record Source(String id, String name, Status status, Instant fetchedAt, String message) {

  /** Result of this run for one source. */
  public enum Status {
    OK,
    ERROR,
    DISABLED
  }
}
