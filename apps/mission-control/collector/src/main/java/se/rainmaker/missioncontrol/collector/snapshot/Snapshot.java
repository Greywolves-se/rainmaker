package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;
import java.util.List;

/**
 * Everything the dashboard shows, written by the collector in one run. {@code sprint} is null when
 * no sprint is running.
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
    List<Document> documents) {

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
}
