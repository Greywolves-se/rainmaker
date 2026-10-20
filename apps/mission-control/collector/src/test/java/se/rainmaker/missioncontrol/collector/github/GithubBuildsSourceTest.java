package se.rainmaker.missioncontrol.collector.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.snapshot.Build;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import se.rainmaker.missioncontrol.collector.snapshot.Release;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import tools.jackson.databind.json.JsonMapper;

class GithubBuildsSourceTest {

  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
  private static final String REPO = "example-org/rainmaker";
  private static final String WORKFLOW = "ci.yml";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final ProjectSettings MISSION_CONTROL =
      new ProjectSettings("mission-control", "Mission Control", "mission-control", null);
  private static final ProjectSettings RAINMAKER =
      new ProjectSettings("rainmaker", "Rainmaker", null, "mission-control");

  @RegisterExtension
  static WireMockExtension github =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  /** By default: no release branches, no runs, no releases, and a develop commit. */
  @BeforeEach
  void quietRepository() {
    stubReleaseBranches(List.of());
    github.stubFor(
        get(urlPathMatching("/repos/" + REPO + "/actions/workflows/.*/runs"))
            .atPriority(10)
            .willReturn(okJson(runsAnswer(List.of()))));
    stubReleases(List.of());
    stubCommit("develop", "abcdef1234567890", "2026-10-20T06:45:00Z");
  }

  @Test
  void latestRunPerWorkflowAndBranch() {
    stubReleaseBranches(List.of("release/1.9.0", "release/1.10.0", "release/next"));
    stubCommit("sha-release-1.10.0", "sha-release-1.10.0", "2026-10-18T10:00:00Z");
    stubRuns("main", List.of(workflowRun(11, "completed", "success")));
    stubRuns(
        "develop",
        List.of(workflowRun(21, "completed", "success"), workflowRun(20, "completed", "failure")));
    stubRuns("release/1.10.0", List.of(workflowRun(31, "in_progress", null)));

    Snapshot snapshot = run(MISSION_CONTROL);

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.builds())
        .extracting(Build::id, Build::branch, Build::status, Build::finishedAt)
        .containsExactly(
            tuple("gh-rainmaker-run-11", "main", Build.Status.SUCCESS, finished(11)),
            tuple("gh-rainmaker-run-21", "develop", Build.Status.SUCCESS, finished(21)),
            tuple("gh-rainmaker-run-31", "release/1.10.0", Build.Status.RUNNING, null));
    Build first = snapshot.builds().getFirst();
    assertThat(first.workflow()).isEqualTo("Mission Control CI");
    assertThat(first.startedAt()).isEqualTo(Instant.parse("2026-10-20T08:00:00Z"));
    assertThat(first.url()).isEqualTo("https://github.com/" + REPO + "/actions/runs/11");
    // Only the latest run is needed: ask for a short page, not GitHub's 100.
    github.verify(
        getRequestedFor(urlPathEqualTo(runsPath(WORKFLOW)))
            .withQueryParam("branch", equalTo("main"))
            .withQueryParam("per_page", equalTo("10")));
  }

  @ParameterizedTest(name = "{0}:{1} -> {2}")
  @CsvSource({
    "completed, success, SUCCESS",
    "completed, failure, FAILURE",
    "completed, timed_out, FAILURE",
    "completed, startup_failure, FAILURE",
    "completed, action_required, FAILURE",
    "completed, cancelled, CANCELLED",
    "queued, , RUNNING",
    "in_progress, , RUNNING",
    "waiting, , RUNNING"
  })
  void runStatus(String status, String conclusion, Build.Status expected) {
    stubRuns("main", List.of(workflowRun(11, status, conclusion)));

    assertThat(run(MISSION_CONTROL).builds()).extracting(Build::status).containsExactly(expected);
  }

  @Test
  void skippedRunsArePassedOver() {
    stubRuns(
        "main",
        List.of(
            workflowRun(13, "completed", "skipped"),
            workflowRun(12, "completed", "neutral"),
            workflowRun(11, "completed", "success")));

    assertThat(run(MISSION_CONTROL).builds())
        .extracting(Build::id)
        .containsExactly("gh-rainmaker-run-11");
  }

  @Test
  void noRunsYetGivesNoBuildsAndOnlyTheDevVersion() {
    Snapshot snapshot = run(MISSION_CONTROL);

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.builds()).isEmpty();
    assertThat(snapshot.events()).isEmpty();
    assertThat(snapshot.releases())
        .extracting(Release::id, Release::version, Release::environment, Release::publishedAt)
        .containsExactly(
            tuple(
                "gh-rainmaker-dev",
                "abcdef1",
                Release.Environment.DEV,
                Instant.parse("2026-10-20T06:45:00Z")));
    assertThat(snapshot.releases().getFirst().url())
        .isEqualTo("https://github.com/" + REPO + "/commit/abcdef1234567890");
  }

  @Test
  void failedBuildOnDevelopCreatesEvent() {
    stubRuns("main", List.of(workflowRun(11, "completed", "failure")));
    stubRuns("develop", List.of(workflowRun(21, "completed", "failure")));

    Snapshot snapshot = run(MISSION_CONTROL);

    // Only develop: main is a release, and its builds are seen on the Development page.
    assertThat(snapshot.events())
        .extracting(Event::id, Event::occurredAt, Event::title, Event::url)
        .containsExactly(
            tuple(
                "gh-rainmaker-run-21-failed",
                finished(21),
                "Build failed on develop: Mission Control CI",
                "https://github.com/" + REPO + "/actions/runs/21"));
  }

  @Test
  void versionPerEnvironment() {
    stubReleaseBranches(List.of("release/1.2.0", "release/1.10.0"));
    stubCommit("sha-release-1.10.0", "sha-release-1.10.0", "2026-10-18T10:00:00Z");
    stubReleases(
        List.of(
            release("v1.11.0-rc1", false, true),
            release("v1.10.0", false, false),
            release("v1.9.0", false, false)));

    assertThat(run(MISSION_CONTROL).releases())
        .extracting(Release::id, Release::version, Release::environment)
        .containsExactly(
            tuple("gh-rainmaker-dev", "abcdef1", Release.Environment.DEV),
            tuple("gh-rainmaker-uat", "1.10.0", Release.Environment.UAT),
            tuple("gh-rainmaker-prod", "v1.10.0", Release.Environment.PROD));
  }

  @Test
  void draftsAndPreReleasesAreNotProduction() {
    stubReleases(List.of(release("v2.0.0", true, false), release("v2.0.0-rc1", false, true)));

    assertThat(run(MISSION_CONTROL).releases())
        .extracting(Release::environment)
        .containsExactly(Release.Environment.DEV);
  }

  @Test
  void missingWorkflowIsAnErrorThatNamesIt() {
    github.stubFor(
        get(urlPathEqualTo(runsPath(WORKFLOW)))
            .willReturn(aResponse().withStatus(404).withBody("{\"message\": \"Not Found\"}")));

    Source result = run(MISSION_CONTROL).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).contains(WORKFLOW, "HTTP 404");
  }

  @Test
  void projectWithoutWorkflowsIsDisabledAndCostsNoCalls() {
    Source result = run(RAINMAKER).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.DISABLED);
    assertThat(result.message()).isEqualTo("No GitHub workflows for this project");
    assertThat(github.getAllServeEvents()).isEmpty();
  }

  private static Snapshot run(ProjectSettings project) {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    GithubProperties properties =
        new GithubProperties(
            URI.create(github.baseUrl()),
            "token",
            List.of(REPO),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            1,
            Duration.ofMillis(1),
            Duration.ofSeconds(60),
            5,
            Map.of("mission-control", List.of(WORKFLOW)));
    GithubBuildsSource source =
        new GithubBuildsSource(new GithubClient(properties, clock), properties);
    CollectorProperties collector =
        new CollectorProperties(List.of(project), Path.of("unused"), Map.of());
    return new CollectorPipeline(List.of(source), collector, new SnapshotValidator(), clock)
        .run(project);
  }

  private static String runsPath(String workflow) {
    return "/repos/" + REPO + "/actions/workflows/" + workflow + "/runs";
  }

  private static void stubRuns(String branch, List<Map<String, Object>> runs) {
    github.stubFor(
        get(urlPathEqualTo(runsPath(WORKFLOW)))
            .withQueryParam("branch", equalTo(branch))
            .willReturn(okJson(runsAnswer(runs))));
  }

  private static String runsAnswer(List<Map<String, Object>> runs) {
    return JSON.writeValueAsString(Map.of("total_count", runs.size(), "workflow_runs", runs));
  }

  private static void stubReleaseBranches(List<String> branches) {
    List<Map<String, Object>> refs = new ArrayList<>();
    for (String branch : branches) {
      refs.add(
          Map.of(
              "ref",
              "refs/heads/" + branch,
              "object",
              Map.of("sha", "sha-" + branch.replace('/', '-'))));
    }
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/git/matching-refs/heads/release/"))
            .willReturn(okJson(JSON.writeValueAsString(refs))));
  }

  private static void stubCommit(String ref, String sha, String date) {
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/commits/" + ref))
            .willReturn(
                okJson(
                    JSON.writeValueAsString(
                        Map.of(
                            "sha",
                            sha,
                            "html_url",
                            "https://github.com/" + REPO + "/commit/" + sha,
                            "commit",
                            Map.of("committer", Map.of("date", date)))))));
  }

  private static void stubReleases(List<Map<String, Object>> releases) {
    github.stubFor(
        get(urlPathEqualTo("/repos/" + REPO + "/releases"))
            .willReturn(okJson(JSON.writeValueAsString(releases))));
  }

  private static Map<String, Object> release(String tag, boolean draft, boolean preRelease) {
    Map<String, Object> release = new LinkedHashMap<>();
    release.put("tag_name", tag);
    release.put("draft", draft);
    release.put("prerelease", preRelease);
    release.put("published_at", draft ? null : "2026-10-17T09:00:00Z");
    release.put("html_url", "https://github.com/" + REPO + "/releases/tag/" + tag);
    return release;
  }

  /** A workflow run; it finished {@code id} minutes after 08:00 unless it is still going. */
  private static Map<String, Object> workflowRun(int id, String status, String conclusion) {
    Map<String, Object> run = new LinkedHashMap<>();
    run.put("id", id);
    run.put("name", "Mission Control CI");
    run.put("head_branch", "ignored: the branch asked for is used");
    run.put("status", status);
    run.put("conclusion", conclusion);
    run.put("run_started_at", "2026-10-20T08:00:00Z");
    run.put("updated_at", finished(id).toString());
    run.put("html_url", "https://github.com/" + REPO + "/actions/runs/" + id);
    return run;
  }

  private static Instant finished(int id) {
    return Instant.parse("2026-10-20T08:00:00Z").plus(Duration.ofMinutes(id));
  }
}
