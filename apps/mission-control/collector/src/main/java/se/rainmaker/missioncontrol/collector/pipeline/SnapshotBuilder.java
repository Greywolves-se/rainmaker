package se.rainmaker.missioncontrol.collector.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import se.rainmaker.missioncontrol.collector.snapshot.Build;
import se.rainmaker.missioncontrol.collector.snapshot.Change;
import se.rainmaker.missioncontrol.collector.snapshot.Decision;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import se.rainmaker.missioncontrol.collector.snapshot.Link;
import se.rainmaker.missioncontrol.collector.snapshot.Project;
import se.rainmaker.missioncontrol.collector.snapshot.PullRequest;
import se.rainmaker.missioncontrol.collector.snapshot.Question;
import se.rainmaker.missioncontrol.collector.snapshot.Release;
import se.rainmaker.missioncontrol.collector.snapshot.Risk;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import se.rainmaker.missioncontrol.collector.snapshot.Sprint;
import se.rainmaker.missioncontrol.collector.snapshot.WorkItem;

/**
 * What a source contributes to the snapshot. Each source gets its own builder; the pipeline adds a
 * contribution to the snapshot only when the source succeeded, so a source that fails halfway
 * leaves nothing behind. Ids must be unique within each list.
 */
public final class SnapshotBuilder {

  private Project project;
  private Sprint sprint;
  private final ItemList<WorkItem> workItems = new ItemList<>("workItems", WorkItem::id);
  private final ItemList<PullRequest> pullRequests =
      new ItemList<>("pullRequests", PullRequest::id);
  private final ItemList<Build> builds = new ItemList<>("builds", Build::id);
  private final ItemList<Release> releases = new ItemList<>("releases", Release::id);
  private final ItemList<Decision> decisions = new ItemList<>("decisions", Decision::id);
  private final ItemList<Risk> risks = new ItemList<>("risks", Risk::id);
  private final ItemList<Question> questions = new ItemList<>("questions", Question::id);
  private final ItemList<Link> links = new ItemList<>("links", Link::id);
  private final ItemList<Change> changes = new ItemList<>("changes", Change::id);
  private final ItemList<Event> events = new ItemList<>("events", Event::id);

  /** Sets the project and its milestones (only one source may do this). */
  public SnapshotBuilder project(Project project) {
    this.project = Objects.requireNonNull(project);
    return this;
  }

  /** Sets the current sprint (only one source may do this). */
  public SnapshotBuilder sprint(Sprint sprint) {
    this.sprint = Objects.requireNonNull(sprint);
    return this;
  }

  /** Adds a work item. */
  public SnapshotBuilder addWorkItem(WorkItem item) {
    workItems.add(item);
    return this;
  }

  /** Adds a pull request. */
  public SnapshotBuilder addPullRequest(PullRequest item) {
    pullRequests.add(item);
    return this;
  }

  /** Adds a CI run. */
  public SnapshotBuilder addBuild(Build item) {
    builds.add(item);
    return this;
  }

  /** Adds a release. */
  public SnapshotBuilder addRelease(Release item) {
    releases.add(item);
    return this;
  }

  /** Adds a decision. */
  public SnapshotBuilder addDecision(Decision item) {
    decisions.add(item);
    return this;
  }

  /** Adds a risk. */
  public SnapshotBuilder addRisk(Risk item) {
    risks.add(item);
    return this;
  }

  /** Adds a question. */
  public SnapshotBuilder addQuestion(Question item) {
    questions.add(item);
    return this;
  }

  /** Adds a link. */
  public SnapshotBuilder addLink(Link item) {
    links.add(item);
    return this;
  }

  /** Adds a change log entry. */
  public SnapshotBuilder addChange(Change item) {
    changes.add(item);
    return this;
  }

  /** Adds an event. */
  public SnapshotBuilder addEvent(Event item) {
    events.add(item);
    return this;
  }

  /** Problems that stop {@code contribution} from being added; empty when it fits. */
  List<String> conflictsWith(SnapshotBuilder contribution) {
    List<String> problems = new ArrayList<>();
    if (project != null && contribution.project != null) {
      problems.add("project is already set by another source");
    }
    if (sprint != null && contribution.sprint != null) {
      problems.add("sprint is already set by another source");
    }
    List<ItemList<?>> ours = lists();
    List<ItemList<?>> theirs = contribution.lists();
    for (int i = 0; i < ours.size(); i++) {
      problems.addAll(ours.get(i).conflictsWith(theirs.get(i)));
    }
    return problems;
  }

  /** Adds a contribution that has no conflicts (see {@link #conflictsWith}). */
  void add(SnapshotBuilder contribution) {
    if (contribution.project != null) {
      project = contribution.project;
    }
    if (contribution.sprint != null) {
      sprint = contribution.sprint;
    }
    workItems.addAll(contribution.workItems);
    pullRequests.addAll(contribution.pullRequests);
    builds.addAll(contribution.builds);
    releases.addAll(contribution.releases);
    decisions.addAll(contribution.decisions);
    risks.addAll(contribution.risks);
    questions.addAll(contribution.questions);
    links.addAll(contribution.links);
    changes.addAll(contribution.changes);
    events.addAll(contribution.events);
  }

  Snapshot build(Instant generatedAt, Project fallbackProject, List<Source> sources) {
    return new Snapshot(
        Snapshot.SCHEMA_VERSION,
        generatedAt,
        project != null ? project : fallbackProject,
        sources,
        sprint,
        workItems.items(),
        pullRequests.items(),
        builds.items(),
        releases.items(),
        decisions.items(),
        risks.items(),
        questions.items(),
        links.items(),
        changes.items(),
        events.items());
  }

  private List<ItemList<?>> lists() {
    return List.of(
        workItems,
        pullRequests,
        builds,
        releases,
        decisions,
        risks,
        questions,
        links,
        changes,
        events);
  }
}
