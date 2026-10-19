package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the dashboard shows, written by the collector in one run. {@code sprint} is null when
 * no sprint is running; {@code navigator} is null when the AI step is off or failed (#115).
 */
public record Snapshot(
    int schemaVersion,
    Instant generatedAt,
    Project project,
    List<Source> sources,
    Sprint sprint,
    List<WorkItem> workItems,
    List<PullRequest> pullRequests,
    List<Build> builds,
    List<Release> releases,
    List<Decision> decisions,
    List<Risk> risks,
    List<Question> questions,
    List<Link> links,
    List<Change> changes,
    List<Event> events,
    List<Document> documents,
    Navigator navigator) {

  /** The contract version these records implement. */
  public static final int SCHEMA_VERSION = 1;

  /** Copies every list, so a snapshot cannot change after it is built. */
  public Snapshot {
    sources = List.copyOf(sources);
    workItems = List.copyOf(workItems);
    pullRequests = List.copyOf(pullRequests);
    builds = List.copyOf(builds);
    releases = List.copyOf(releases);
    decisions = List.copyOf(decisions);
    risks = List.copyOf(risks);
    questions = List.copyOf(questions);
    links = List.copyOf(links);
    changes = List.copyOf(changes);
    events = List.copyOf(events);
    documents = List.copyOf(documents);
  }

  /**
   * This snapshot with the Navigator's AI text and the AI step's own entry in {@code sources}
   * (#115). The step runs after all sources, so it adds itself last.
   */
  public Snapshot withNavigator(Navigator navigator, Source step) {
    List<Source> withStep = new ArrayList<>(sources);
    withStep.add(step);
    return new Snapshot(
        schemaVersion,
        generatedAt,
        project,
        withStep,
        sprint,
        workItems,
        pullRequests,
        builds,
        releases,
        decisions,
        risks,
        questions,
        links,
        changes,
        events,
        documents,
        navigator);
  }
}
