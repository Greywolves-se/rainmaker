package se.rainmaker.missioncontrol.collector.github;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub settings ({@code mission-control.collector.github.*}).
 *
 * @param apiUrl GitHub REST API address
 * @param token read-only token; may be empty, then the GitHub source reports an error
 * @param repositories repositories to read, as {@code owner/name}
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for an answer
 * @param maxAttempts attempts per request, including the first
 * @param backoff wait before the first retry; it doubles for each retry
 * @param rateLimitMaxWait longest rate-limit wait worth sitting out; longer limits fail at once
 * @param maxPages most pages read per list, a guard against endless pagination
 * @param workflows the workflow files whose builds belong to each project, by project id (#14),
 *     e.g. {@code mission-control-ci.yml}; a project not listed has no builds
 */
@ConfigurationProperties("mission-control.collector.github")
public record GithubProperties(
    URI apiUrl,
    String token,
    List<String> repositories,
    Duration connectTimeout,
    Duration readTimeout,
    int maxAttempts,
    Duration backoff,
    Duration rateLimitMaxWait,
    int maxPages,
    Map<String, List<String>> workflows) {

  private static final Pattern REPOSITORY = Pattern.compile("^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$");

  /** A file name in {@code .github/workflows}; it ends up in a request path. */
  private static final Pattern WORKFLOW = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9._-]*\\.ya?ml$");

  /** Checks the settings at startup; repository names end up in request paths. */
  public GithubProperties {
    Objects.requireNonNull(apiUrl, "mission-control.collector.github.api-url must be set");
    repositories = repositories == null ? List.of() : List.copyOf(repositories);
    for (String repository : repositories) {
      if (!REPOSITORY.matcher(repository).matches() || repository.contains("..")) {
        throw new IllegalArgumentException(
            "GitHub repository '" + repository + "' must be written as owner/name");
      }
    }
    Objects.requireNonNull(connectTimeout, "connect-timeout must be set");
    Objects.requireNonNull(readTimeout, "read-timeout must be set");
    Objects.requireNonNull(backoff, "backoff must be set");
    Objects.requireNonNull(rateLimitMaxWait, "rate-limit-max-wait must be set");
    if (maxAttempts < 1 || maxPages < 1) {
      throw new IllegalArgumentException("max-attempts and max-pages must be at least 1");
    }
    workflows = workflows == null ? Map.of() : Map.copyOf(workflows);
    workflows.forEach(
        (project, files) -> {
          for (String file : files) {
            if (!WORKFLOW.matcher(file).matches()) {
              throw new IllegalArgumentException(
                  "GitHub workflow '"
                      + file
                      + "' for project '"
                      + project
                      + "' must be a file name such as ci.yml");
            }
          }
        });
  }

  /** The workflow files whose builds belong to {@code projectId}; empty when none are set. */
  public List<String> workflowsFor(String projectId) {
    return List.copyOf(workflows.getOrDefault(projectId, List.of()));
  }

  /** Whether a token is configured. */
  public boolean hasToken() {
    return token != null && !token.isBlank();
  }

  /** Like the generated one, but the token is never shown, so the settings are safe to log. */
  @Override
  public String toString() {
    return "GithubProperties[apiUrl="
        + apiUrl
        + ", token="
        + (hasToken() ? "***" : "(none)")
        + ", repositories="
        + repositories
        + ", connectTimeout="
        + connectTimeout
        + ", readTimeout="
        + readTimeout
        + ", maxAttempts="
        + maxAttempts
        + ", backoff="
        + backoff
        + ", rateLimitMaxWait="
        + rateLimitMaxWait
        + ", maxPages="
        + maxPages
        + ", workflows="
        + workflows
        + "]";
  }
}
