package se.rainmaker.publishercontrol.articles;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How G3 reads G2's articles API (#150, contract 7.1), under {@code
 * rainmaker.publisher-control.articles.*}. Checked at start-up.
 *
 * @param baseUrl G2's address, e.g. {@code http://localhost:8082}
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for the answer
 * @param maxAttempts how many times to ask in one poll, the first included
 * @param backoff wait before the first retry; it doubles for each further retry
 * @param pollInterval time between the end of one poll and the start of the next
 * @param polling {@code false} turns the schedule off, e.g. in tests that start the whole app
 */
@ConfigurationProperties(prefix = "rainmaker.publisher-control.articles")
public record ArticlesProperties(
    URI baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    int maxAttempts,
    Duration backoff,
    Duration pollInterval,
    @DefaultValue("true") boolean polling) {

  /** Checks the values. */
  public ArticlesProperties {
    require(baseUrl != null && baseUrl.getHost() != null, "base-url must be an absolute URL");
    require(
        connectTimeout != null && connectTimeout.isPositive(), "connect-timeout must be positive");
    require(readTimeout != null && readTimeout.isPositive(), "read-timeout must be positive");
    require(maxAttempts >= 1, "max-attempts must be at least 1");
    require(backoff != null && backoff.isPositive(), "backoff must be positive");
    require(pollInterval != null && pollInterval.isPositive(), "poll-interval must be positive");
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new IllegalArgumentException("rainmaker.publisher-control.articles: " + problem);
    }
  }
}
