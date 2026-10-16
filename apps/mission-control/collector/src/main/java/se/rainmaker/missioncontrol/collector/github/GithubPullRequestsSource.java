package se.rainmaker.missioncontrol.collector.github;

import static se.rainmaker.missioncontrol.collector.github.GithubFields.instant;
import static se.rainmaker.missioncontrol.collector.github.GithubFields.itemId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.SnapshotBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceCollector;
import se.rainmaker.missioncontrol.collector.pipeline.SourceException;
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
 * runs do not fail a pull request. Only check runs are read: CI that reports through the older
 * commit Status API is not seen. With GitHub Actions that is everything.
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

  private static final Logger log = LoggerFactory.getLogger(GithubPullRequestsSource.class);
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
      // Keyed by number, so a pull request merged between the two calls is included once, as
      // merged (the later answer wins).
      Map<Integer, JsonNode> included = new LinkedHashMap<>();
      for (JsonNode pull : client.getAll(pulls, Map.of("state", "open"))) {
        included.put(pull.get("number").asInt(), pull);
      }
      for (JsonNode pull : recentlyMerged(pulls, since)) {
        included.put(pull.get("number").asInt(), pull);
      }

      for (JsonNode pull : included.values()) {
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

  /** Pull requests merged since {@code since}, from the newest page of closed ones. */
  private List<JsonNode> recentlyMerged(String pulls, Instant since) {
    // The newest 100 closed pull requests are plenty for one week; no need to page through all.
    JsonNode closed =
        client.getFirstPage(
            pulls, Map.of("state", "closed", "sort", "updated", "direction", "desc"));
    if (!closed.isArray()) {
      throw new SourceException("GitHub answered " + pulls + " with something that is not a list");
    }
    return closed
        .valueStream()
        .filter(
            pull -> {
              Instant mergedAt = instant(pull, "merged_at");
              return mergedAt != null && mergedAt.isAfter(since);
            })
        .toList();
  }

  /**
   * One pull request with its review state and checks. All or nothing on purpose: if the reviews or
   * check runs of one pull request cannot be read (403, timeout), the whole source shows an error.
   * The contract has no "unknown" review or check state, and showing "pending" or "none" instead
   * would present wrong data as fact.
   */
  private PullRequest pullRequest(String repository, JsonNode pull) {
    int number = pull.get("number").asInt();
    Instant mergedAt = instant(pull, "merged_at");
    // Only open and recently merged pull requests are included, never closed unmerged ones.
    PullRequest.State state = mergedAt != null ? PullRequest.State.MERGED : PullRequest.State.OPEN;
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
    JsonNode total = answer.get("total_count");
    if (total != null && total.asInt() > runs.size()) {
      // Only the first page is read; a commit with more check runs is unusual for this team.
      log.atWarn()
          .addKeyValue("checkRuns", total.asInt())
          .addKeyValue("read", runs.size())
          .log("more check runs than one page; only the first page counts");
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
