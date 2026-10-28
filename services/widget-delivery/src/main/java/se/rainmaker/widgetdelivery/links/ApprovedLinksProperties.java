package se.rainmaker.widgetdelivery.links;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How S1 reaches G3's approved links API (#151, contract 7.3), under {@code
 * rainmaker.widget-delivery.approved-links.*}. Checked at start-up. The reader waits at most two
 * seconds for the panel (rainmaker.js), so the timeouts, the attempts and the back-off together
 * must stay well under that.
 *
 * @param baseUrl G3's address, e.g. {@code http://localhost:8083}
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for the answer
 * @param maxAttempts how many times to ask, the first included
 * @param backoff wait before the first retry; it doubles for each further retry
 * @param circuitBreaker when to stop calling G3 for a while
 */
@ConfigurationProperties(prefix = "rainmaker.widget-delivery.approved-links")
record ApprovedLinksProperties(
    URI baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    int maxAttempts,
    Duration backoff,
    CircuitBreaker circuitBreaker) {

  /** Checks the values. */
  ApprovedLinksProperties {
    require(baseUrl != null && baseUrl.getHost() != null, "base-url must be an absolute URL");
    require(
        connectTimeout != null && connectTimeout.isPositive(), "connect-timeout must be positive");
    require(readTimeout != null && readTimeout.isPositive(), "read-timeout must be positive");
    require(maxAttempts >= 1, "max-attempts must be at least 1");
    require(backoff != null && backoff.isPositive(), "backoff must be positive");
    require(circuitBreaker != null, "circuit-breaker.* must be set");
  }

  /**
   * The circuit breaker: among the last {@code windowSize} requests, once at least {@code
   * minimumCalls} were made and {@code failureRatePercent} of them failed, G3 isn't called for
   * {@code openFor}. Then one trial request decides whether to call it again.
   *
   * @param windowSize how many of the latest requests count
   * @param minimumCalls how many requests are needed before the rate counts
   * @param failureRatePercent the share of failed requests that opens the circuit, 1–100
   * @param openFor how long G3 isn't called once the circuit is open
   */
  record CircuitBreaker(
      int windowSize, int minimumCalls, int failureRatePercent, Duration openFor) {

    /** Checks the values. */
    CircuitBreaker {
      require(windowSize >= 1, "circuit-breaker.window-size must be at least 1");
      require(minimumCalls >= 1, "circuit-breaker.minimum-calls must be at least 1");
      require(
          failureRatePercent >= 1 && failureRatePercent <= 100,
          "circuit-breaker.failure-rate-percent must be 1-100");
      require(openFor != null && openFor.isPositive(), "circuit-breaker.open-for must be positive");
    }
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new IllegalArgumentException("rainmaker.widget-delivery.approved-links: " + problem);
    }
  }
}
