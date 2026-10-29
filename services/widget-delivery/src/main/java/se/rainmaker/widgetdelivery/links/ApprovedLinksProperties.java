package se.rainmaker.widgetdelivery.links;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How S1 reaches G3's approved links API (#151, contract 7.3), under {@code
 * rainmaker.widget-delivery.approved-links.*}. Checked at start-up. The reader waits at most two
 * seconds for the panel (rainmaker.js), so the timeouts, the attempts and the back-off together
 * must stay under that; G1 checks it at start-up (#224).
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

  /**
   * How long the reader's browser waits for the widget API before it gives up on the panel
   * (rainmaker.js, {@code API_TIMEOUT_MS} in {@code widget/src/links-api.ts}).
   */
  static final Duration READER_WAIT = Duration.ofSeconds(2);

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
   * The longest G1 can wait for G3: every attempt timing out on both connecting and reading, with
   * the back-off before each retry. Stops adding once it reaches {@link #READER_WAIT}.
   */
  Duration worstCaseWait() {
    Duration attempt = connectTimeout.plus(readTimeout);
    Duration wait = attempt;
    Duration nextBackoff = backoff;
    for (int retry = 2; retry <= maxAttempts && wait.compareTo(READER_WAIT) < 0; retry++) {
      wait = wait.plus(nextBackoff).plus(attempt);
      nextBackoff = nextBackoff.multipliedBy(2);
    }
    return wait;
  }

  /**
   * Checked when G1 starts (#224): past the reader's wait, rainmaker.js has given up and G1 retries
   * for nobody. Not checked in the constructor, so tests can use generous timeouts.
   */
  void requireWithinReaderWait() {
    require(
        worstCaseWait().compareTo(READER_WAIT) < 0,
        "attempts x (connect-timeout + read-timeout) + back-off must stay under the reader's "
            + READER_WAIT.toMillis()
            + " ms");
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
