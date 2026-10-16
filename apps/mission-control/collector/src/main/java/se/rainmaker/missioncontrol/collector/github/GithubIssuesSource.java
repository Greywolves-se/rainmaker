package se.rainmaker.missioncontrol.collector.github;

import static se.rainmaker.missioncontrol.collector.github.GithubFields.instant;
import static se.rainmaker.missioncontrol.collector.github.GithubFields.itemId;
import static se.rainmaker.missioncontrol.collector.github.GithubFields.labels;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.pipeline.SnapshotBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceCollector;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import se.rainmaker.missioncontrol.collector.snapshot.Sprint;
import se.rainmaker.missioncontrol.collector.snapshot.WorkItem;
import tools.jackson.databind.JsonNode;

/**
 * Issues and the current sprint from the configured GitHub repositories (#12).
 *
 * <p>Status rules, in this order: closed is {@code done}; the label {@code blocked} is {@code
 * blocked}; the label {@code status: in progress}, or an open issue with an assignee, is {@code
 * in_progress}; anything else is {@code todo}. Labels are compared without case.
 *
 * <p>Only the issues whose labels belong to the project are read (#99, {@link
 * ProjectSettings#includes}). The sprint is chosen among the open milestones that hold at least one
 * of those issues: the one with the nearest due date across all repositories; its start date is the
 * day the milestone was created. A project with no issues in a milestone has no sprint. Pull
 * requests, which the issues endpoint also returns, are left out (#13 handles them).
 */
@Component
public class GithubIssuesSource implements SourceCollector {

  static final String ID = "github";

  /** Closed issues older than this are left out, so the board shows recent work. */
  static final Duration KEEP_CLOSED = Duration.ofDays(30);

  /** Issues closed within this time also appear in the recent-events feed. */
  static final Duration RECENT = Duration.ofDays(7);

  private final GithubClient client;
  private final GithubProperties properties;
  private final Clock clock;

  /** Reads the repositories in the settings with the given client. */
  public GithubIssuesSource(GithubClient client, GithubProperties properties, Clock clock) {
    this.client = client;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "GitHub";
  }

  @Override
  public void collect(ProjectSettings project, SnapshotBuilder snapshot) {
    Instant now = clock.instant();
    Map<String, List<JsonNode>> issuesByRepository = new LinkedHashMap<>();
    for (String repository : properties.repositories()) {
      issuesByRepository.put(
          repository,
          client.getAll("/repos/" + repository + "/issues", Map.of("state", "all")).stream()
              .filter(issue -> !issue.has("pull_request"))
              .filter(issue -> project.includes(labels(issue)))
              .toList());
    }
    Optional<SprintMilestone> sprint = currentSprint(milestonesOf(issuesByRepository));
    sprint.ifPresent(found -> snapshot.sprint(found.sprint()));
    for (Map.Entry<String, List<JsonNode>> entry : issuesByRepository.entrySet()) {
      String repository = entry.getKey();
      for (JsonNode issue : entry.getValue()) {
        Instant closedAt = instant(issue, "closed_at");
        if (closedAt != null && closedAt.isBefore(now.minus(KEEP_CLOSED))) {
          continue;
        }
        WorkItem item = workItem(repository, issue, sprint, closedAt);
        snapshot.addWorkItem(item);
        if (closedAt != null && closedAt.isAfter(now.minus(RECENT))) {
          snapshot.addEvent(
              new Event(
                  item.id() + "-closed",
                  closedAt,
                  "Issue #" + issue.get("number").asInt() + " closed: " + item.title(),
                  ID,
                  item.url()));
        }
      }
    }
  }

  private WorkItem workItem(
      String repository, JsonNode issue, Optional<SprintMilestone> sprint, Instant closedAt) {
    List<String> labels = labels(issue);
    List<String> assignees =
        issue.get("assignees").valueStream().map(user -> user.get("login").asString()).toList();
    JsonNode milestone = issue.get("milestone");
    boolean inCurrentSprint =
        sprint.isPresent()
            && sprint.get().repository().equals(repository)
            && milestone != null
            && !milestone.isNull()
            && milestone.get("number").asInt() == sprint.get().number();
    return new WorkItem(
        itemId(repository, issue.get("number").asInt()),
        issue.get("title").asString(),
        status(issue.get("state").asString(), labels, !assignees.isEmpty()),
        assignees,
        labels,
        inCurrentSprint,
        instant(issue, "created_at"),
        closedAt,
        ID,
        issue.get("html_url").asString());
  }

  /** The status rules from the class comment. */
  static WorkItem.Status status(String state, List<String> labels, boolean assigned) {
    if ("closed".equals(state)) {
      return WorkItem.Status.DONE;
    }
    List<String> lower = labels.stream().map(label -> label.toLowerCase(Locale.ROOT)).toList();
    if (lower.contains("blocked")) {
      return WorkItem.Status.BLOCKED;
    }
    if (lower.contains("status: in progress") || assigned) {
      return WorkItem.Status.IN_PROGRESS;
    }
    return WorkItem.Status.TODO;
  }

  /**
   * The open milestone due today or later with the nearest due date. If every open milestone is
   * overdue (a sprint left open by mistake), the most recently overdue one. Milestones without a
   * due date are not sprints.
   */
  private Optional<SprintMilestone> currentSprint(Set<MilestoneRef> projectMilestones) {
    if (projectMilestones.isEmpty()) {
      return Optional.empty();
    }
    List<SprintMilestone> candidates =
        properties.repositories().stream()
            .flatMap(
                repository ->
                    client
                        .getAll("/repos/" + repository + "/milestones", Map.of("state", "open"))
                        .stream()
                        .filter(milestone -> instant(milestone, "due_on") != null)
                        .map(milestone -> toSprint(repository, milestone)))
            .filter(found -> projectMilestones.contains(found.ref()))
            .toList();
    LocalDate today = date(clock.instant());
    Comparator<SprintMilestone> byDueDate = Comparator.comparing(found -> found.sprint().endDate());
    return candidates.stream()
        .filter(found -> !found.sprint().endDate().isBefore(today))
        .min(byDueDate)
        .or(() -> candidates.stream().max(byDueDate));
  }

  private static SprintMilestone toSprint(String repository, JsonNode milestone) {
    Sprint sprint =
        new Sprint(
            milestone.get("title").asString(),
            date(instant(milestone, "created_at")),
            date(instant(milestone, "due_on")),
            firstLine(milestone.get("description")),
            ID,
            milestone.get("html_url").asString());
    return new SprintMilestone(repository, milestone.get("number").asInt(), sprint);
  }

  /** The first non-empty line of a milestone description, as the sprint goal. */
  private static String firstLine(JsonNode description) {
    if (description == null || description.isNull()) {
      return null;
    }
    return description
        .asString()
        .lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty())
        .findFirst()
        .orElse(null);
  }

  private static LocalDate date(Instant instant) {
    return LocalDate.ofInstant(instant, ZoneOffset.UTC);
  }

  /** The milestones that hold at least one of the project's issues (open or closed). */
  private static Set<MilestoneRef> milestonesOf(Map<String, List<JsonNode>> issuesByRepository) {
    Set<MilestoneRef> milestones = new HashSet<>();
    issuesByRepository.forEach(
        (repository, issues) ->
            issues.stream()
                .map(issue -> issue.get("milestone"))
                .filter(milestone -> milestone != null && !milestone.isNull())
                .forEach(
                    milestone ->
                        milestones.add(
                            new MilestoneRef(repository, milestone.get("number").asInt()))));
    return milestones;
  }

  /** A milestone, by repository and number. */
  private record MilestoneRef(String repository, int number) {}

  /** The sprint and the milestone it came from, to mark the work items in it. */
  private record SprintMilestone(String repository, int number, Sprint sprint) {

    MilestoneRef ref() {
      return new MilestoneRef(repository, number);
    }
  }
}
