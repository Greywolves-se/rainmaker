package se.rainmaker.missioncontrol.collector.github;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import se.rainmaker.missioncontrol.collector.snapshot.PullRequest;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class GithubPullRequestsSourceTest {

  private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
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

  @ParameterizedTest(name = "reviews [{0}] -> {1}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
                                                             | PENDING
          alice:APPROVED                                     | APPROVED
          alice:CHANGES_REQUESTED                            | CHANGES_REQUESTED
          alice:COMMENTED                                    | PENDING
          alice:CHANGES_REQUESTED, alice:APPROVED            | APPROVED
          alice:APPROVED, bob:CHANGES_REQUESTED              | CHANGES_REQUESTED
          alice:CHANGES_REQUESTED, alice:DISMISSED           | PENDING
          alice:CHANGES_REQUESTED, alice:DISMISSED, alice:APPROVED | APPROVED
          """)
  void reviewState(String reviews, PullRequest.Review expected) {
    stubOpen(List.of(pullRequest(5, false, null)));
    stubClosed(List.of());
    stubReviews(5, reviews == null ? List.of() : List.of(reviews.split(",\\s*")));
    stubChecks(5, List.of());

    assertThat(run().pullRequests()).extracting(PullRequest::review).containsExactly(expected);
  }

  @ParameterizedTest(name = "check runs [{0}] -> {1}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
                                                  | NONE
          completed:success, completed:skipped    | PASSING
          completed:success, in_progress:         | PENDING
          completed:failure, in_progress:         | FAILING
          completed:cancelled                     | PASSING
          completed:timed_out                     | FAILING
          completed:action_required               | FAILING
          completed:startup_failure               | FAILING
          """)
  void checksStatus(String runs, PullRequest.Checks expected) {
    stubOpen(List.of(pullRequest(5, false, null)));
    stubClosed(List.of());
    stubReviews(5, List.of());
    stubChecks(5, runs == null ? List.of() : List.of(runs.split(",\\s*")));

    assertThat(run().pullRequests()).extracting(PullRequest::checks).containsExactly(expected);
  }

  @Test
  void mapsOpenAndDraftPullRequests() {
    stubOpen(List.of(pullRequest(5, false, null), pullRequest(6, true, null)));
    stubClosed(List.of());
    for (int number : new int[] {5, 6}) {
      stubReviews(number, List.of());
      stubChecks(number, List.of());
    }

    Snapshot snapshot = run();

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.pullRequests())
        .extracting(PullRequest::id, PullRequest::draft, PullRequest::state)
        .containsExactly(
            tuple("gh-rainmaker-5", false, PullRequest.State.OPEN),
            tuple("gh-rainmaker-6", true, PullRequest.State.OPEN));
    PullRequest first = snapshot.pullRequests().getFirst();
    assertThat(first.author()).isEqualTo("mai");
    assertThat(first.title()).isEqualTo("PR 5");
    assertThat(first.mergedAt()).isNull();
    assertThat(first.url()).isEqualTo("https://github.com/" + REPO + "/pull/5");
  }

  @Test
  void pullRequestsMergedInTheLastWeekAreIncludedWithAnEvent() {
    stubOpen(List.of());
    stubClosed(
        List.of(
            pullRequest(7, false, NOW.minus(Duration.ofDays(1))),
            pullRequest(8, false, NOW.minus(Duration.ofDays(10))),
            closedWithoutMerge(9)));
    stubReviews(7, List.of("alice:APPROVED"));
    stubChecks(7, List.of("completed:success"));

    Snapshot snapshot = run();

    assertThat(snapshot.pullRequests())
        .extracting(PullRequest::id, PullRequest::state, PullRequest::mergedAt)
        .containsExactly(
            tuple("gh-rainmaker-7", PullRequest.State.MERGED, NOW.minus(Duration.ofDays(1))));
    assertThat(snapshot.events())
        .extracting(Event::id, Event::title)
        .containsExactly(tuple("gh-rainmaker-7-merged", "PR #7 merged: PR 7"));
  }

  @Test
  void pullRequestMergedBetweenTheTwoCallsIsIncludedOnceAsMerged() {
    stubOpen(List.of(pullRequest(7, false, null)));
    stubClosed(List.of(pullRequest(7, false, NOW.minus(Duration.ofMinutes(1)))));
    stubReviews(7, List.of());
    stubChecks(7, List.of());

    Snapshot snapshot = run();

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.pullRequests())
        .extracting(PullRequest::id, PullRequest::state)
        .containsExactly(tuple("gh-rainmaker-7", PullRequest.State.MERGED));
  }

  @Test
  void closedPullRequestsAnswerThatIsNotListIsError() {
    stubOpen(List.of());
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/pulls"))
            .withQueryParam("state", equalTo("closed"))
            .willReturn(okJson("{\"message\": \"unexpected\"}")));

    Source result = run().sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).contains("not a list");
  }

  @Test
  void moreCheckRunsThanOnePageIsLogged(CapturedOutput output) {
    JsonNode answer =
        JSON.readTree(
            "{\"total_count\": 150, \"check_runs\": "
                + "[{\"status\": \"completed\", \"conclusion\": \"success\"}]}");

    assertThat(GithubPullRequestsSource.checks(answer)).isEqualTo(PullRequest.Checks.PASSING);
    assertThat(output.getOut()).contains("only the first page counts");
  }

  @Test
  void withoutTokenTheSourceIsAnError() {
    Source result = run(" ", ALL).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).isEqualTo("No GitHub token: set MC_GITHUB_TOKEN");
  }

  @Test
  void pullRequestBelongsToProjectByLabelAndOthersCostNoCalls() {
    Map<String, Object> labelled = pullRequest(1, false, null);
    labelled.put("labels", List.of(Map.of("name", "mission-control")));
    stubOpen(List.of(labelled, pullRequest(2, false, null)));
    stubClosed(List.of());
    stubReviews(1, List.of());
    stubChecks(1, List.of());

    Snapshot snapshot = run("token", MISSION_CONTROL);

    assertThat(snapshot.pullRequests()).extracting(PullRequest::number).containsExactly(1);
    github.verify(0, getRequestedFor(urlPathEqualTo("/repos/" + REPO + "/pulls/2/reviews")));
  }

  @Test
  void pullRequestWithoutLabelBelongsToRainmaker() {
    Map<String, Object> labelled = pullRequest(1, false, null);
    labelled.put("labels", List.of(Map.of("name", "mission-control")));
    stubOpen(List.of(labelled, pullRequest(2, false, null)));
    stubClosed(List.of());
    stubReviews(2, List.of());
    stubChecks(2, List.of());

    assertThat(run("token", RAINMAKER).pullRequests())
        .extracting(PullRequest::number)
        .containsExactly(2);
  }

  private static Snapshot run() {
    return run("token", ALL);
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
            5);
    GithubPullRequestsSource source =
        new GithubPullRequestsSource(new GithubClient(properties, clock), properties, clock);
    CollectorProperties collector =
        new CollectorProperties(List.of(project), Path.of("unused"), null, Map.of());
    return new CollectorPipeline(List.of(source), collector, new SnapshotValidator(), clock)
        .run(project);
  }

  private static void stubOpen(List<Map<String, Object>> pulls) {
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/pulls"))
            .withQueryParam("state", equalTo("open"))
            .willReturn(okJson(JSON.writeValueAsString(pulls))));
  }

  private static void stubClosed(List<Map<String, Object>> pulls) {
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/pulls"))
            .withQueryParam("state", equalTo("closed"))
            .withQueryParam("sort", equalTo("updated"))
            .willReturn(okJson(JSON.writeValueAsString(pulls))));
  }

  /** Reviews as {@code reviewer:STATE}, oldest first, like GitHub returns them. */
  private static void stubReviews(int number, List<String> reviews) {
    List<Map<String, Object>> body = new ArrayList<>();
    for (String review : reviews) {
      String[] parts = review.split(":");
      body.add(Map.of("user", Map.of("login", parts[0]), "state", parts[1]));
    }
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/pulls/" + number + "/reviews"))
            .willReturn(okJson(JSON.writeValueAsString(body))));
  }

  /** Check runs as {@code status:conclusion}; the conclusion is empty while running. */
  private static void stubChecks(int number, List<String> runs) {
    List<Map<String, Object>> checkRuns = new ArrayList<>();
    for (String run : runs) {
      String[] parts = run.split(":", -1);
      Map<String, Object> checkRun = new LinkedHashMap<>();
      checkRun.put("status", parts[0]);
      checkRun.put("conclusion", parts[1].isEmpty() ? null : parts[1]);
      checkRuns.add(checkRun);
    }
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/commits/sha-" + number + "/check-runs"))
            .willReturn(
                okJson(
                    JSON.writeValueAsString(
                        Map.of("total_count", checkRuns.size(), "check_runs", checkRuns)))));
  }

  private static Map<String, Object> closedWithoutMerge(int number) {
    Map<String, Object> pull = pullRequest(number, false, null);
    pull.put("state", "closed");
    return pull;
  }

  private static Map<String, Object> pullRequest(int number, boolean draft, Instant mergedAt) {
    Map<String, Object> pull = new LinkedHashMap<>();
    pull.put("number", number);
    pull.put("node_id", "PR_node" + number);
    pull.put("title", "PR " + number);
    pull.put("user", Map.of("login", "mai"));
    pull.put("draft", draft);
    pull.put("state", mergedAt == null ? "open" : "closed");
    pull.put("created_at", "2026-10-14T08:00:00Z");
    pull.put("merged_at", mergedAt == null ? null : mergedAt.toString());
    pull.put("head", Map.of("sha", "sha-" + number));
    pull.put("html_url", "https://github.com/" + REPO + "/pull/" + number);
    return pull;
  }
}
