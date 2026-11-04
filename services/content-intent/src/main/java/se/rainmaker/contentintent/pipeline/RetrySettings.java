package se.rainmaker.contentintent.pipeline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How content-intent classifies failed and unclassified versions again ({@code
 * rainmaker.content-intent.retry.*}, #223, #258).
 *
 * @param interval time between the end of one run and the start of the next, and how long a version
 *     must go untried before a run takes it. At least a minute, longer than one classification with
 *     Bedrock's retries, so a version being classified is never taken. After the n-th failed try in
 *     a row, a version waits {@code interval × 2^(n − 1)} instead (#258).
 * @param maxWait the longest a version waits however often it failed, at least {@code interval}
 * @param batchSize the most versions one run classifies (1–100), so a long outage can't spend the
 *     daily token cap at once
 * @param enabled {@code false} turns the schedule off, e.g. in tests that start the whole app
 */
@ConfigurationProperties("rainmaker.content-intent.retry")
record RetrySettings(
    @DefaultValue("5m") Duration interval,
    @DefaultValue("1d") Duration maxWait,
    @DefaultValue("10") int batchSize,
    @DefaultValue("true") boolean enabled) {

  private static final int MAX_BATCH_SIZE = 100;

  RetrySettings {
    if (interval.compareTo(Duration.ofMinutes(1)) < 0) {
      throw new IllegalArgumentException(
          "rainmaker.content-intent.retry.interval must be at least 1m: " + interval);
    }
    if (maxWait.compareTo(interval) < 0) {
      throw new IllegalArgumentException(
          "rainmaker.content-intent.retry.max-wait must be at least the interval ("
              + interval
              + "): "
              + maxWait);
    }
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "rainmaker.content-intent.retry.batch-size must be from 1 to "
              + MAX_BATCH_SIZE
              + ": "
              + batchSize);
    }
  }
}
