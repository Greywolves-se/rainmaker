package se.rainmaker.missioncontrol.collector.github;

import static se.rainmaker.missioncontrol.collector.github.GithubFields.instant;
import static se.rainmaker.missioncontrol.collector.github.GithubFields.itemId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.SnapshotBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceCollector;
import se.rainmaker.missioncontrol.collector.snapshot.Event;
import se.rainmaker.missioncontrol.collector.snapshot.PullRequest;
import tools.jackson.databind.JsonNode;

/**
 * Open pull requests, and those merged in the last 7 days, from the configured GitHub repositories
 * (#13).
 *
 * <p>Review state: the latest review of each reviewer counts (a dismissed review cancels it). Any
 * "changes requested" wins, then any approval; otherwise the review is pending. Comments do not
 * count.
 *
 * <p>Checks: no check runs is {@code none}; a run that failed, timed out or needs action is {@code
 * failing}; else a run still going is {@code pending}; else {@code passing}. Cancelled and skipped
 * runs do not fail a pull request.
 *
 * <p>Each pull request takes two extra calls (reviews and check runs); the list endpoint has
 * neither. How long a PR has waited for review is calculated by the dashboard from {@code
 * createdAt} and {@code review} (#21); requested reviewers are a follow-up (#94).
 */
@Component
public class GithubPullRequestsSource implements SourceCollector {

  static final String ID = "github-pull-requests";

  /** Pull requests merged within this time are included, with an event. */
  static final Duration RECENT = Duration.ofDays(7);

  private static final Set<String> FAILING =
      Set.of("failure", "timed_out", "action_required", "startup_failure");

  private final GithubClient client;
  private final GithubProperties properties;
  private final Clock clock;

  /** Reads the repositories in the settings with the given client. */
  public GithubPullRequestsSource(GithubClient client, GithubProperties properties, Clock clock) {
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
    return "GitHub pull requests";
  }

  @Override
  public void collect(SnapshotBuilder snapshot) {
    Instant since = clock.instant().minus(RECENT);
    for (String repository : properties.repositories()) {
      String pulls = "/repos/" + repository + "/pulls";
      // The newest 100 closed pull requests are plenty for one week; no need to page through all.
      Map<String, String> recentlyClosed = new LinkedHashMap<>();
      recentlyClosed.put("state", "closed");
      recentlyClosed.put("sort", "updated");
      recentlyClosed.put("direction", "desc");
      List<JsonNode> included = new ArrayList<>(client.getAll(pulls, Map.of("state", "open")));
      client
          .getFirstPage(pulls, recentlyClosed)
          .valueStream()
          .filter(pull -> instant(pull, "merged_at") != null)
          .filter(pull -> instant(pull, "merged_at").isAfter(since))
          .forEach(included::add);

      for (JsonNode pull : included) {
        PullRequest pullRequest = pullRequest(repository, pull);
        snapshot.addPullRequest(pullRequest);
        if (pullRequest.mergedAt() != null) {
          snapshot.addEvent(
              new Event(
                  pullRequest.id() + "-merged",
                  pullRequest.mergedAt(),
                  "PR #" + pullRequest.number() + " merged: " + pullRequest.title(),
                  ID,
                  pullRequest.url()));
        }
      }
    }
  }

  private PullRequest pullRequest(String repository, JsonNode pull) {
    int number = pull.get("number").asInt();
    Instant mergedAt = instant(pull, "merged_at");
    PullRequest.State state =
        mergedAt != null
            ? PullRequest.State.MERGED
            : "open".equals(pull.get("state").asString())
                ? PullRequest.State.OPEN
                : PullRequest.State.CLOSED;
    return new PullRequest(
        itemId(repository, number),
        number,
        pull.get("title").asString(),
        pull.get("user").get("login").asString(),
        pull.get("draft").asBoolean(),
        state,
        review(client.getAll("/repos/" + repository + "/pulls/" + number + "/reviews", Map.of())),
        checks(
            client.getFirstPage(
                "/repos/"
                    + repository
                    + "/commits/"
                    + pull.get("head").get("sha").asString()
                    + "/check-runs",
                Map.of())),
        instant(pull, "created_at"),
        mergedAt,
        ID,
        pull.get("html_url").asString());
  }

  /** The review rules from the class comment. Reviews come oldest first. */
  static PullRequest.Review review(List<JsonNode> reviews) {
    Map<String, String> latest = new LinkedHashMap<>();
    for (JsonNode review : reviews) {
      String state = review.get("state").asString();
      String reviewer = review.get("user").get("login").asString();
      switch (state) {
        case "APPROVED", "CHANGES_REQUESTED" -> latest.put(reviewer, state);
        case "DISMISSED" -> latest.remove(reviewer);
        default -> {
          // COMMENTED and PENDING do not change the review state.
        }
      }
    }
    if (latest.containsValue("CHANGES_REQUESTED")) {
      return PullRequest.Review.CHANGES_REQUESTED;
    }
    return latest.containsValue("APPROVED")
        ? PullRequest.Review.APPROVED
        : PullRequest.Review.PENDING;
  }

  /** The check rules from the class comment, from a {@code check-runs} answer. */
  static PullRequest.Checks checks(JsonNode answer) {
    JsonNode runs = answer.get("check_runs");
    if (runs == null || runs.isEmpty()) {
      return PullRequest.Checks.NONE;
    }
    boolean running = false;
    for (JsonNode run : runs) {
      if (!"completed".equals(run.get("status").asString())) {
        running = true;
      } else if (FAILING.contains(run.get("conclusion").asString())) {
        return PullRequest.Checks.FAILING;
      }
    }
    return running ? PullRequest.Checks.PENDING : PullRequest.Checks.PASSING;
  }
}
