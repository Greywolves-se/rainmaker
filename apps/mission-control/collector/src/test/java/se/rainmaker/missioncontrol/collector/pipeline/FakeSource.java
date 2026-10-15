package se.rainmaker.missioncontrol.collector.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import se.rainmaker.missioncontrol.collector.snapshot.WorkItem;

/** A source for tests: adds the work items it is given, then optionally fails. */
public final class FakeSource implements SourceCollector {

  private final String id;
  private final List<WorkItem> workItems = new ArrayList<>();
  private RuntimeException failure;
  private int calls;

  private FakeSource(String id) {
    this.id = id;
  }

  /** A source that adds nothing until told to. */
  public static FakeSource withId(String id) {
    return new FakeSource(id);
  }

  /** Adds a work item with this id when collected. */
  public FakeSource withWorkItem(String workItemId) {
    workItems.add(workItem(workItemId, "https://github.com/example-org/example-repo/issues/1"));
    return this;
  }

  /** Adds a work item whose url breaks the contract (and contains a value to keep out of logs). */
  public FakeSource withInvalidWorkItem(String workItemId) {
    workItems.add(workItem(workItemId, "not a url ghp_secret"));
    return this;
  }

  /** Fails with this exception after adding its work items. */
  public FakeSource failingWith(RuntimeException exception) {
    this.failure = exception;
    return this;
  }

  /** How often {@link #collect} was called. */
  public int calls() {
    return calls;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String name() {
    return "Fake " + id;
  }

  private WorkItem workItem(String workItemId, String url) {
    return new WorkItem(
        workItemId,
        "Work item " + workItemId,
        WorkItem.Status.TODO,
        List.of(),
        List.of(),
        false,
        Instant.parse("2026-10-15T08:00:00Z"),
        null,
        id,
        url);
  }

  @Override
  public void collect(SnapshotBuilder snapshot) {
    calls++;
    workItems.forEach(snapshot::addWorkItem);
    if (failure != null) {
      throw failure;
    }
  }
}
