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
 * @param recent how many of the most recently stored articles each poll asks for (1–500)
 * @param reader the reader path's call for one article (#230): its own short timeouts, one attempt
 *     and a circuit breaker; the timeouts and retries above are the poll's
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
    @DefaultValue("100") int recent,
    Reader reader,
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
    require(recent >= 1 && recent <= 500, "recent must be from 1 to 500 (contract 7.1)");
    require(reader != null, "reader.* must be set");
  }

  /**
   * How long G1 waits for G3's answer, once connected (G1's {@code
   * rainmaker.widget-delivery.approved-links.read-timeout}, contract 7.3). G3's call to G2 on the
   * reader path must end well before it, or G1 has given up and G3 waits for nobody.
   */
  static final Duration G1_READ_TIMEOUT = Duration.ofMillis(500);

  /**
   * Checked when G3 starts (#230): the reader call's worst case, connecting plus reading once, must
   * stay under {@link #G1_READ_TIMEOUT}. G1's setting lives in another app, so it is mirrored here,
   * as S1 mirrors rainmaker.js's wait. Not checked in the constructor, so tests can use generous
   * timeouts.
   */
  void requireReaderWithinG1Wait() {
    require(
        reader.connectTimeout().plus(reader.readTimeout()).compareTo(G1_READ_TIMEOUT) < 0,
        "reader.connect-timeout + reader.read-timeout must stay under G1's "
            + G1_READ_TIMEOUT.toMillis()
            + " ms");
  }

  /**
   * The reader path's call for one article ({@code ?url=…}, contract 7.3): one attempt, no retry.
   *
   * @param connectTimeout how long to wait for a connection
   * @param readTimeout how long to wait for the answer
   * @param circuitBreaker when to stop calling G2 for a while because it keeps failing
   */
  public record Reader(
      Duration connectTimeout, Duration readTimeout, CircuitBreaker circuitBreaker) {

    /** Checks the values. */
    public Reader {
      require(
          connectTimeout != null && connectTimeout.isPositive(),
          "reader.connect-timeout must be positive");
      require(
          readTimeout != null && readTimeout.isPositive(), "reader.read-timeout must be positive");
      require(circuitBreaker != null, "reader.circuit-breaker.* must be set");
    }
  }

  /**
   * Among the last {@code windowSize} reader calls, once at least {@code minimumCalls} were made
   * and {@code failureRatePercent} of them failed, G2 isn't called for {@code openFor}. Then one
   * trial call decides whether to call it again.
   *
   * @param windowSize how many of the latest calls count
   * @param minimumCalls how many calls are needed before the rate counts
   * @param failureRatePercent the share of failed calls that opens the circuit, 1–100
   * @param openFor how long G2 isn't called once the circuit is open
   */
  public record CircuitBreaker(
      int windowSize, int minimumCalls, int failureRatePercent, Duration openFor) {

    /** Checks the values. */
    public CircuitBreaker {
      require(windowSize >= 1, "reader.circuit-breaker.window-size must be at least 1");
      require(minimumCalls >= 1, "reader.circuit-breaker.minimum-calls must be at least 1");
      require(
          failureRatePercent >= 1 && failureRatePercent <= 100,
          "reader.circuit-breaker.failure-rate-percent must be from 1 to 100");
      require(
          openFor != null && openFor.isPositive(),
          "reader.circuit-breaker.open-for must be positive");
    }
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new IllegalArgumentException("rainmaker.publisher-control.articles: " + problem);
    }
  }
}
