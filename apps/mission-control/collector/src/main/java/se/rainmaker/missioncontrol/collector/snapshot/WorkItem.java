package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;
import java.util.List;

/** An issue or story. {@code closedAt} is null while it is open. */
public record WorkItem(
    String id,
    String title,
    Status status,
    List<String> assignees,
    List<String> labels,
    boolean inCurrentSprint,
    Instant createdAt,
    Instant closedAt,
    String source,
    String url) {

  /** Copies the lists, so the work item cannot change after it is built. */
  public WorkItem {
    assignees = List.copyOf(assignees);
    labels = List.copyOf(labels);
  }

  /** Board column. */
  public enum Status {
    TODO,
    IN_PROGRESS,
    BLOCKED,
    DONE
  }
}
