package se.rainmaker.contentintent.pipeline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How failed and unclassified article versions are classified again (#223), under {@code
 * rainmaker.content-intent.retry.*}. Checked at start-up, so a wrong setting stops G2 instead of
 * misbehaving later.
 *
 * @param interval how often the retry runs, and how long a version waits after a try before it is
 *     tried again
 * @param batchSize most versions tried in one run, so a long outage can't use the whole daily token
 *     cap at once
 */
@ConfigurationProperties(prefix = "rainmaker.content-intent.retry")
public record RetryProperties(Duration interval, int batchSize) {

  /** The most versions one run may try. */
  static final int MAX_BATCH_SIZE = 100;

  /** Checks the values. */
  public RetryProperties {
    if (interval == null || interval.isNegative() || interval.isZero()) {
      throw new IllegalArgumentException(
          "rainmaker.content-intent.retry.interval must be positive: " + interval);
    }
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "rainmaker.content-intent.retry.batch-size must be 1-"
              + MAX_BATCH_SIZE
              + ": "
              + batchSize);
    }
  }
}
