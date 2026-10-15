package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;

/** A pull request. {@code mergedAt} is null unless it is merged. */
public record PullRequest(
    String id,
    int number,
    String title,
    String author,
    boolean draft,
    State state,
    Review review,
    Checks checks,
    Instant createdAt,
    Instant mergedAt,
    String source,
    String url) {

  /** Whether the pull request is open, merged or closed without merging. */
  public enum State {
    OPEN,
    MERGED,
    CLOSED
  }

  /** Overall review state. */
  public enum Review {
    PENDING,
    APPROVED,
    CHANGES_REQUESTED
  }

  /** Combined result of the CI checks. */
  public enum Checks {
    PASSING,
    FAILING,
    PENDING,
    NONE
  }
}
