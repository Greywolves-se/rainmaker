package se.rainmaker.missioncontrol.collector.github;

import static se.rainmaker.missioncontrol.collector.github.GithubFields.instant;
import static se.rainmaker.missioncontrol.collector.github.GithubFields.repositoryName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>Builds: the latest run of each of the project's workflows ({@code
 * mission-control.collector.github.workflows.<project>}) on {@code main}, {@code develop} and the
 * newest {@code release/x.y.z} branch. Skipped and neutral runs are passed over. A failed build on
 * {@code develop} adds an event. A project without workflows shows the source as disabled.
 *
 * <p>Versions, derived from the Git Flow branches until real deployments are read: dev is the
 * latest commit on {@code develop}, UAT the newest {@code release/x.y.z} branch and production the
 * latest published GitHub release (not a draft or pre-release).
 */
@Component
public class GithubBuildsSource implements SourceCollector {

  static final String ID = "github-builds";

  /** Enough to find the latest run that was not skipped. */
  static final int RUNS_PER_BRANCH = 10;

  private static final Pattern RELEASE_BRANCH =
      Pattern.compile("^refs/heads/(release/(\\d+)\\.(\\d+)\\.(\\d+))$");
  private static final Set<String> FAILED =
      Set.of("failure", "timed_out", "action_required", "startup_failure");
  private static final Set<String> NOT_A_RESULT = Set.of("skipped", "neutral", "stale");

  private final GithubClient client;
  private final GithubProperties properties;

  /** Reads the repositories in the settings with the given client. */
  public GithubBuildsSource(GithubClient client, GithubProperties properties) {
    this.client = client;
    this.properties = properties;
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
    for (String repository : properties.repositories()) {
      Optional<ReleaseBranch> releaseBranch = newestReleaseBranch(repository);
      List<String> branches = new ArrayList<>(List.of("main", "develop"));
      releaseBranch.ifPresent(branch -> branches.add(branch.name()));
      for (String workflow : properties.workflowsFor(project.id())) {
        for (String branch : branches) {
          latestBuild(repository, workflow, branch).ifPresent(build -> add(build, snapshot));
        }
      }
      addVersions(repository, releaseBranch, snapshot);
    }
  }

  private static void add(Build build, SnapshotBuilder snapshot) {
    snapshot.addBuild(build);
    if (build.status() == Build.Status.FAILURE && "develop".equals(build.branch())) {
      snapshot.addEvent(
          new Event(
              build.id() + "-failed",
              build.finishedAt(),
              "Build failed on develop: " + build.workflow(),
              ID,
              build.url()));
    }
  }

  /** The latest run of {@code workflow} on {@code branch} that has a result or is running. */
  private Optional<Build> latestBuild(String repository, String workflow, String branch) {
    String path = "/repos/" + repository + "/actions/workflows/" + workflow + "/runs";
    JsonNode runs =
        client
            .getFirstPage(
                path, Map.of("branch", branch, "per_page", String.valueOf(RUNS_PER_BRANCH)))
            .get("workflow_runs");
    if (runs == null || !runs.isArray()) {
      throw new SourceException("GitHub answered " + path + " without a list of runs");
    }
    // Newest first, as GitHub returns them.
    return runs.valueStream()
        .filter(run -> !NOT_A_RESULT.contains(run.path("conclusion").asString("")))
        .findFirst()
        .map(run -> build(repository, branch, run));
  }

  private static Build build(String repository, String branch, JsonNode run) {
    Build.Status status = status(run);
    return new Build(
        "gh-" + repositoryName(repository) + "-run-" + run.get("id").asLong(),
        run.get("name").asString(),
        branch,
        status,
        instant(run, "run_started_at"),
        status == Build.Status.RUNNING ? null : instant(run, "updated_at"),
        ID,
        run.get("html_url").asString());
  }

  /** Not completed is running; then success, cancelled, or failed for every failing result. */
  static Build.Status status(JsonNode run) {
    if (!"completed".equals(run.get("status").asString())) {
      return Build.Status.RUNNING;
    }
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
      Matcher matcher = RELEASE_BRANCH.matcher(ref.get("ref").asString());
      if (matcher.matches()) {
        branches.add(
            new ReleaseBranch(
                matcher.group(1),
                List.of(
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4))),
                ref.get("object").get("sha").asString()));
      }
    }
    return branches.stream().max(ReleaseBranch.BY_VERSION);
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
    releaseBranch.ifPresent(
        branch ->
            snapshot.addRelease(
                commitRelease(
                    "gh-" + name + "-uat",
                    branch.version(),
                    Release.Environment.UAT,
                    client.getFirstPage(
                        "/repos/" + repository + "/commits/" + branch.sha(), Map.of()))));
    latestPublishedRelease(repository)
        .ifPresent(
            release ->
                snapshot.addRelease(
                    new Release(
                        "gh-" + name + "-prod",
                        release.get("tag_name").asString(),
                        Release.Environment.PROD,
                        instant(release, "published_at"),
                        ID,
                        release.get("html_url").asString())));
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

  /** The newest GitHub release that is published: not a draft and not a pre-release. */
  private Optional<JsonNode> latestPublishedRelease(String repository) {
    JsonNode releases = client.getFirstPage("/repos/" + repository + "/releases", Map.of());
    if (!releases.isArray()) {
      throw new SourceException(
          "GitHub answered /repos/" + repository + "/releases with something that is not a list");
    }
    return releases
        .valueStream()
        .filter(
            release ->
                !release.path("draft").asBoolean() && !release.path("prerelease").asBoolean())
        .findFirst();
  }

  /** A {@code release/x.y.z} branch: its name, version numbers and head commit. */
  private record ReleaseBranch(String name, List<Integer> numbers, String sha) {

    static final Comparator<ReleaseBranch> BY_VERSION =
        Comparator.comparing((ReleaseBranch branch) -> branch.numbers().get(0))
            .thenComparing(branch -> branch.numbers().get(1))
            .thenComparing(branch -> branch.numbers().get(2));

    String version() {
      return name.substring("release/".length());
    }
  }
}
