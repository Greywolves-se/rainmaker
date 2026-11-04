package se.rainmaker.matchingranking.offers;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How matching reaches G3's offers API (#149, contract 7.2), under {@code
 * rainmaker.matching-ranking.offers.*}. Checked at start-up.
 *
 * @param baseUrl G3's address, e.g. {@code http://localhost:8083}
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for the answer
 * @param maxAttempts how many times to ask, the first included
 * @param backoff wait before the first retry; it doubles for each further retry
 */
@ConfigurationProperties(prefix = "rainmaker.matching-ranking.offers")
public record OffersProperties(
    URI baseUrl, Duration connectTimeout, Duration readTimeout, int maxAttempts, Duration backoff) {

  /** Checks the values. */
  public OffersProperties {
    require(baseUrl != null && baseUrl.getHost() != null, "base-url must be an absolute URL");
    require(
        connectTimeout != null && connectTimeout.isPositive(), "connect-timeout must be positive");
    require(readTimeout != null && readTimeout.isPositive(), "read-timeout must be positive");
    require(maxAttempts >= 1, "max-attempts must be at least 1");
    require(backoff != null && backoff.isPositive(), "backoff must be positive");
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new IllegalArgumentException("rainmaker.matching-ranking.offers: " + problem);
    }
  }
}
