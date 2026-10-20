package se.rainmaker.missioncontrol.collector.github;

import static se.rainmaker.missioncontrol.collector.github.GithubFields.instant;
import static se.rainmaker.missioncontrol.collector.github.GithubFields.repositoryName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.pipeline.SnapshotBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceCollector;
import se.rainmaker.missioncontrol.collector.pipeline.SourceException;
import se.rainmaker.missioncontrol.collector.snapshot.Build;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import se.rainmaker.missioncontrol.collector.snapshot.Release;
import tools.jackson.databind.JsonNode;

/**
 * The latest CI builds and the version in each environment, from the configured GitHub repositories
 * (#14).
 *
 * <p>Builds: the latest <em>completed</em> run of each of the project's workflows ({@code
 * mission-control.collector.github.workflows.<project>}) on {@code main}, {@code develop} and the
 * newest {@code release/x.y.z} branch. A snapshot is frozen until the next run, so a running state
 * would go stale; the collector's own run is never completed while it runs, so it is never shown.
 * Pull request runs, and skipped and neutral runs, are passed over. A failed build on {@code
 * develop} in the last 7 days adds an event. A project without workflows shows the source as
 * disabled, with no builds and no versions.
 *
 * <p>Versions, derived from Git Flow until real deployments are read: dev is the latest commit on
 * {@code develop}, production the published GitHub release (not a draft or pre-release) with the
 * highest {@code x.y.z} tag, and UAT the newest {@code release/x.y.z} branch, left out when it is
 * not newer than production (a finished release branch that was not deleted).
 *
 * <p>Assumes one repository: each project's workflow files are looked up in every repository in
 * {@code repositories}, and a repository without them makes the source an error.
 */
@Component
public class GithubBuildsSource implements SourceCollector {

  static final String ID = "github-builds";

  /** Enough to find the latest completed run that was not skipped or a pull request run. */
  static final int RUNS_PER_BRANCH = 10;

  /** Failed builds within this time add an event, like the other sources' events. */
  static final Duration RECENT = Duration.ofDays(7);

  private static final String RELEASE_REF = "refs/heads/release/";
  private static final Set<String> FAILED =
      Set.of("failure", "timed_out", "action_required", "startup_failure");
  private static final Set<String> NOT_A_RESULT = Set.of("skipped", "neutral", "stale");

  private final GithubClient client;
  private final GithubProperties properties;
  private final Clock clock;

  /** Reads the repositories in the settings with the given client. */
  public GithubBuildsSource(GithubClient client, GithubProperties properties, Clock clock) {
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
    return "GitHub builds and versions";
  }

  @Override
  public Optional<String> notSetUpFor(ProjectSettings project) {
    return properties.workflowsFor(project.id()).isEmpty()
        ? Optional.of("No GitHub workflows for this project")
        : Optional.empty();
  }

  @Override
  public void collect(ProjectSettings project, SnapshotBuilder snapshot) {
    Instant recent = clock.instant().minus(RECENT);
    for (String repository : properties.repositories()) {
      Optional<ReleaseBranch> releaseBranch = newestReleaseBranch(repository);
      List<String> branches = new ArrayList<>(List.of("main", "develop"));
      releaseBranch.ifPresent(branch -> branches.add(branch.name()));
      for (String workflow : properties.workflowsFor(project.id())) {
        for (String branch : branches) {
          latestBuild(repository, workflow, branch)
              .ifPresent(build -> add(build, recent, snapshot));
        }
      }
      addVersions(repository, releaseBranch, snapshot);
    }
  }

  private static void add(Build build, Instant recent, SnapshotBuilder snapshot) {
    snapshot.addBuild(build);
    if (build.status() == Build.Status.FAILURE
        && "develop".equals(build.branch())
        && build.finishedAt().isAfter(recent)) {
      snapshot.addEvent(
          new Event(
              build.id() + "-failed",
              build.finishedAt(),
              "Build failed on develop: " + build.workflow(),
              ID,
              build.url()));
    }
  }

  /** The latest completed run of {@code workflow} on {@code branch} that counts as a build. */
  private Optional<Build> latestBuild(String repository, String workflow, String branch) {
    String path = "/repos/" + repository + "/actions/workflows/" + workflow + "/runs";
    Map<String, String> query =
        Map.of(
            "branch", branch, "status", "completed", "per_page", String.valueOf(RUNS_PER_BRANCH));
    JsonNode runs = client.getFirstPage(path, query).get("workflow_runs");
    if (runs == null || !runs.isArray()) {
      throw new SourceException("GitHub answered " + path + " without a list of runs");
    }
    // Newest first, as GitHub returns them. The checks repeat the query's filter on purpose.
    return runs.valueStream()
        .filter(run -> "completed".equals(run.path("status").asString("")))
        .filter(run -> !"pull_request".equals(run.path("event").asString("")))
        .filter(run -> !NOT_A_RESULT.contains(run.path("conclusion").asString("")))
        .findFirst()
        .map(run -> build(repository, branch, run));
  }

  private static Build build(String repository, String branch, JsonNode run) {
    return new Build(
        "gh-" + repositoryName(repository) + "-run-" + run.get("id").asLong(),
        run.get("name").asString(),
        branch,
        status(run),
        instant(run, "run_started_at"),
        instant(run, "updated_at"),
        ID,
        run.get("html_url").asString());
  }

  /** A completed run: failed for every failing result, cancelled, otherwise success. */
  static Build.Status status(JsonNode run) {
    String conclusion = run.path("conclusion").asString("");
    if (FAILED.contains(conclusion)) {
      return Build.Status.FAILURE;
    }
    return "cancelled".equals(conclusion) ? Build.Status.CANCELLED : Build.Status.SUCCESS;
  }

  /** The {@code release/x.y.z} branch with the highest version; other names are ignored. */
  private Optional<ReleaseBranch> newestReleaseBranch(String repository) {
    List<ReleaseBranch> branches = new ArrayList<>();
    for (JsonNode ref :
        client.getAll("/repos/" + repository + "/git/matching-refs/heads/release/", Map.of())) {
      String name = ref.get("ref").asString();
      if (name.startsWith(RELEASE_REF)) {
        ReleaseVersion.parse(name.substring(RELEASE_REF.length()))
            .ifPresent(
                version ->
                    branches.add(
                        new ReleaseBranch(
                            name.substring("refs/heads/".length()),
                            version,
                            ref.get("object").get("sha").asString())));
      }
    }
    return branches.stream().max(Comparator.comparing(ReleaseBranch::version));
  }

  private void addVersions(
      String repository, Optional<ReleaseBranch> releaseBranch, SnapshotBuilder snapshot) {
    String name = repositoryName(repository);
    JsonNode develop = client.getFirstPage("/repos/" + repository + "/commits/develop", Map.of());
    snapshot.addRelease(
        commitRelease(
            "gh-" + name + "-dev",
            develop.get("sha").asString().substring(0, 7),
            Release.Environment.DEV,
            develop));
    Optional<PublishedRelease> production = production(repository);
    releaseBranch
        .filter(
            branch ->
                production.isEmpty() || branch.version().compareTo(production.get().version()) > 0)
        .ifPresent(
            branch ->
                snapshot.addRelease(
                    commitRelease(
                        "gh-" + name + "-uat",
                        branch.name().substring("release/".length()),
                        Release.Environment.UAT,
                        client.getFirstPage(
                            "/repos/" + repository + "/commits/" + branch.sha(), Map.of()))));
    production.ifPresent(
        release ->
            snapshot.addRelease(
                new Release(
                    "gh-" + name + "-prod",
                    release.json().get("tag_name").asString(),
                    Release.Environment.PROD,
                    instant(release.json(), "published_at"),
                    ID,
                    release.json().get("html_url").asString())));
  }

  private static Release commitRelease(
      String id, String version, Release.Environment environment, JsonNode commit) {
    return new Release(
        id,
        version,
        environment,
        instant(commit.get("commit").get("committer"), "date"),
        ID,
        commit.get("html_url").asString());
  }

  /**
   * The published release (not a draft or pre-release) with the highest {@code x.y.z} tag, so a
   * hotfix on an older line never replaces a newer version. Tags that are not versions are ignored.
   */
  private Optional<PublishedRelease> production(String repository) {
    JsonNode releases = client.getFirstPage("/repos/" + repository + "/releases", Map.of());
    if (!releases.isArray()) {
      throw new SourceException(
          "GitHub answered /repos/" + repository + "/releases with something that is not a list");
    }
    List<PublishedRelease> published = new ArrayList<>();
    for (JsonNode release : releases) {
      if (!release.path("draft").asBoolean() && !release.path("prerelease").asBoolean()) {
        ReleaseVersion.parse(release.path("tag_name").asString(""))
            .ifPresent(version -> published.add(new PublishedRelease(version, release)));
      }
    }
    return published.stream().max(Comparator.comparing(PublishedRelease::version));
  }

  /** A {@code release/x.y.z} branch: its name, version and head commit. */
  private record ReleaseBranch(String name, ReleaseVersion version, String sha) {}

  /** A published GitHub release and the version in its tag. */
  private record PublishedRelease(ReleaseVersion version, JsonNode json) {}
}
