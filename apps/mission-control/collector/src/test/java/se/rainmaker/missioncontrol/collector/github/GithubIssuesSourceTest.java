package se.rainmaker.missioncontrol.collector.github;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import se.rainmaker.missioncontrol.collector.snapshot.WorkItem;
import tools.jackson.databind.json.JsonMapper;

class GithubIssuesSourceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
  private static final String REPO = "example-org/rainmaker";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final ProjectSettings ALL = new ProjectSettings("all", "All", null, null);
  private static final ProjectSettings RAINMAKER =
      new ProjectSettings("rainmaker", "Rainmaker", null, "mission-control");
  private static final ProjectSettings MISSION_CONTROL =
      new ProjectSettings("mission-control", "Mission Control", "mission-control", null);

  @RegisterExtension
  static WireMockExtension github =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @ParameterizedTest(name = "{0}, labels [{1}], assignee {2} -> {3}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          closed | blocked              | true  | DONE
          open   | blocked              | true  | BLOCKED
          open   | Blocked              | false | BLOCKED
          open   | status: in progress  | false | IN_PROGRESS
          open   | bug                  | true  | IN_PROGRESS
          open   | bug                  | false | TODO
          """)
  void statusRules(String state, String label, boolean assigned, WorkItem.Status expected) {
    stubIssues(List.of(issue(1, state, List.of(label), assigned, null, "closed".equals(state))));
    stubMilestones(List.of());

    assertThat(run().workItems()).extracting(WorkItem::status).containsExactly(expected);
  }

  @Test
  void mapsIssuesToWorkItemsAndLeavesOutPullRequests() {
    Map<String, Object> pullRequest = issue(8, "open", List.of(), false, null, false);
    pullRequest.put("pull_request", Map.of("url", "https://api.github.com/x"));
    stubIssues(List.of(issue(7, "open", List.of("area: web"), true, null, false), pullRequest));
    stubMilestones(List.of());

    Snapshot snapshot = run();

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    WorkItem item = snapshot.workItems().getFirst();
    assertThat(snapshot.workItems()).hasSize(1);
    assertThat(item.id()).isEqualTo("gh-rainmaker-7");
    assertThat(item.title()).isEqualTo("Issue 7");
    assertThat(item.assignees()).containsExactly("mai");
    assertThat(item.labels()).containsExactly("area: web");
    assertThat(item.source()).isEqualTo("github");
    assertThat(item.url()).isEqualTo("https://github.com/" + REPO + "/issues/7");
  }

  @Test
  void sprintIsTheOpenMilestoneWithTheNearestDueDate() {
    stubIssues(
        List.of(
            issue(1, "open", List.of(), false, 2, false),
            issue(2, "open", List.of(), false, 3, false)));
    stubMilestones(
        List.of(
            milestone(3, "Sprint 3", "2026-11-06T07:00:00Z", "Later sprint"),
            milestone(2, "Sprint 2", "2026-10-23T07:00:00Z", "Show real data\nMore text"),
            milestone(9, "Backlog", null, null)));

    Snapshot snapshot = run();

    assertThat(snapshot.sprint().name()).isEqualTo("Sprint 2");
    assertThat(snapshot.sprint().startDate()).isEqualTo(LocalDate.of(2026, 10, 12));
    assertThat(snapshot.sprint().endDate()).isEqualTo(LocalDate.of(2026, 10, 23));
    assertThat(snapshot.sprint().goal()).isEqualTo("Show real data");
    assertThat(snapshot.workItems())
        .extracting(WorkItem::inCurrentSprint)
        .containsExactly(true, false);
  }

  @Test
  void overdueMilestoneLeftOpenDoesNotHideTheNextSprint() {
    stubIssues(inMilestones(1, 2));
    stubMilestones(
        List.of(
            milestone(1, "Sprint 1", "2026-10-10T07:00:00Z", null),
            milestone(2, "Sprint 2", "2026-10-23T07:00:00Z", null)));

    assertThat(run().sprint().name()).isEqualTo("Sprint 2");
  }

  @Test
  void milestoneDueTodayIsStillTheCurrentSprint() {
    stubIssues(inMilestones(2, 3));
    stubMilestones(
        List.of(
            milestone(2, "Sprint 2", "2026-10-15T07:00:00Z", null),
            milestone(3, "Sprint 3", "2026-10-29T07:00:00Z", null)));

    assertThat(run().sprint().name()).isEqualTo("Sprint 2");
  }

  @Test
  void whenEveryMilestoneIsOverdueTheMostRecentOneIsTheSprint() {
    stubIssues(inMilestones(1, 2));
    stubMilestones(
        List.of(
            milestone(1, "Sprint 1", "2026-10-01T07:00:00Z", null),
            milestone(2, "Sprint 2", "2026-10-10T07:00:00Z", null)));

    assertThat(run().sprint().name()).isEqualTo("Sprint 2");
  }

  @Test
  void issuesClosedMoreThanThirtyDaysAgoAreDropped() {
    stubIssues(
        List.of(
            closedIssue(1, NOW.minus(Duration.ofDays(29))),
            closedIssue(2, NOW.minus(Duration.ofDays(31))),
            issue(3, "open", List.of(), false, null, false)));
    stubMilestones(List.of());

    assertThat(run().workItems())
        .extracting(WorkItem::id)
        .containsExactly("gh-rainmaker-1", "gh-rainmaker-3");
  }

  @Test
  void noMilestoneWithDueDateMeansNoSprint() {
    stubIssues(inMilestones(9));
    stubMilestones(List.of(milestone(9, "Backlog", null, null)));

    assertThat(run().sprint()).isNull();
  }

  @Test
  void issuesClosedInTheLastWeekBecomeEvents() {
    stubIssues(
        List.of(
            closedIssue(1, NOW.minus(Duration.ofDays(2))),
            closedIssue(2, NOW.minus(Duration.ofDays(20)))));
    stubMilestones(List.of());

    List<Event> events = run().events();

    assertThat(events).extracting(Event::id).containsExactly("gh-rainmaker-1-closed");
    assertThat(events.getFirst().title()).isEqualTo("Issue #1 closed: Issue 1");
  }

  @Test
  void eachProjectGetsOnlyItsLabelledIssues() {
    stubIssues(
        List.of(
            issue(1, "open", List.of("Mission-Control"), false, null, false),
            issue(2, "open", List.of("bug"), false, null, false),
            issue(3, "open", List.of(), false, null, false)));
    stubMilestones(List.of());

    assertThat(run(MISSION_CONTROL).workItems())
        .extracting(WorkItem::id)
        .containsExactly("gh-rainmaker-1");
    assertThat(run(RAINMAKER).workItems())
        .extracting(WorkItem::id)
        .containsExactly("gh-rainmaker-2", "gh-rainmaker-3");
  }

  @Test
  void sprintIsChosenAmongTheMilestonesOfTheProjectsIssues() {
    stubIssues(
        List.of(
            issue(1, "open", List.of("mission-control"), false, 3, false),
            issue(2, "open", List.of(), false, 2, false)));
    stubMilestones(
        List.of(
            milestone(3, "Mission Control V1", "2026-11-06T07:00:00Z", null),
            milestone(2, "Sprint 2", "2026-10-23T07:00:00Z", null)));

    Snapshot missionControl = run(MISSION_CONTROL);

    assertThat(missionControl.sprint().name()).isEqualTo("Mission Control V1");
    assertThat(missionControl.workItems().getFirst().inCurrentSprint()).isTrue();
    assertThat(run(RAINMAKER).sprint().name()).isEqualTo("Sprint 2");
  }

  @Test
  void projectWithNoIssuesInMilestoneHasNoSprint() {
    stubIssues(List.of(issue(1, "open", List.of("mission-control"), false, 3, false)));
    stubMilestones(List.of(milestone(3, "Mission Control V1", "2026-11-06T07:00:00Z", null)));

    assertThat(run(RAINMAKER).sprint()).isNull();
  }

  @Test
  void withoutTokenTheSourceIsAnErrorWithClearMessage() {
    Source result = run(" ", ALL).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).isEqualTo("No GitHub token: set MC_GITHUB_TOKEN");
  }

  private static Snapshot run() {
    return run("token", ALL);
  }

  private static Snapshot run(ProjectSettings project) {
    return run("token", project);
  }

  private static Snapshot run(String token, ProjectSettings project) {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    GithubProperties properties =
        new GithubProperties(
            URI.create(github.baseUrl()),
            token,
            List.of(REPO),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            1,
            Duration.ofMillis(1),
            Duration.ofSeconds(60),
            5,
            Map.of());
    GithubIssuesSource source =
        new GithubIssuesSource(new GithubClient(properties, clock), properties, clock);
    CollectorProperties collector =
        new CollectorProperties(List.of(project), Path.of("unused"), Map.of());
    return new CollectorPipeline(List.of(source), collector, new SnapshotValidator(), clock)
        .run(project);
  }

  /** One open issue in each of these milestones, numbered like the milestone. */
  private static List<Map<String, Object>> inMilestones(int... milestones) {
    List<Map<String, Object>> issues = new ArrayList<>();
    for (int milestone : milestones) {
      issues.add(issue(milestone, "open", List.of(), false, milestone, false));
    }
    return issues;
  }

  private static void stubIssues(List<Map<String, Object>> issues) {
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/issues"))
            .withQueryParam("state", equalTo("all"))
            .willReturn(okJson(JSON.writeValueAsString(issues))));
  }

  private static void stubMilestones(List<Map<String, Object>> milestones) {
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/milestones"))
            .withQueryParam("state", equalTo("open"))
            .willReturn(okJson(JSON.writeValueAsString(milestones))));
  }

  private static Map<String, Object> issue(
      int number,
      String state,
      List<String> labels,
      boolean assigned,
      Integer milestone,
      boolean closed) {
    Map<String, Object> issue = new LinkedHashMap<>();
    issue.put("number", number);
    issue.put("title", "Issue " + number);
    issue.put("state", state);
    List<Map<String, Object>> labelObjects = new ArrayList<>();
    labels.forEach(label -> labelObjects.add(Map.of("name", label)));
    issue.put("labels", labelObjects);
    issue.put("assignees", assigned ? List.of(Map.of("login", "mai")) : List.of());
    issue.put("milestone", milestone == null ? null : Map.of("number", milestone));
    issue.put("created_at", "2026-10-12T08:00:00Z");
    issue.put("closed_at", closed ? "2026-10-14T08:00:00Z" : null);
    issue.put("html_url", "https://github.com/" + REPO + "/issues/" + number);
    return issue;
  }

  private static Map<String, Object> closedIssue(int number, Instant closedAt) {
    Map<String, Object> issue = issue(number, "closed", List.of(), false, null, true);
    issue.put("closed_at", closedAt.toString());
    return issue;
  }

  private static Map<String, Object> milestone(
      int number, String title, String dueOn, String description) {
    Map<String, Object> milestone = new LinkedHashMap<>();
    milestone.put("number", number);
    milestone.put("title", title);
    milestone.put("description", description);
    milestone.put("due_on", dueOn);
    milestone.put("created_at", "2026-10-12T09:00:00Z");
    milestone.put("html_url", "https://github.com/" + REPO + "/milestone/" + number);
    return milestone;
  }
}
