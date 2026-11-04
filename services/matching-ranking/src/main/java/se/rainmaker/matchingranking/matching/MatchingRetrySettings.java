package se.rainmaker.matchingranking.matching;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How matching matches green articles again when G3's offers API was down ({@code
 * rainmaker.matching-ranking.retry.*}, #248).
 *
 * @param interval time between the end of one run and the start of the next, and how long an
 *     article must go untried before a run takes it. At least a minute, so a G3 that stays down is
 *     asked at most once a minute.
 * @param batchSize the most articles one run matches (1–100); a run asks G3 once, whatever the size
 * @param enabled {@code false} turns the schedule off, e.g. in tests that start the whole app
 */
@ConfigurationProperties("rainmaker.matching-ranking.retry")
record MatchingRetrySettings(
    @DefaultValue("5m") Duration interval,
    @DefaultValue("50") int batchSize,
    @DefaultValue("true") boolean enabled) {

  private static final int MAX_BATCH_SIZE = 100;

  MatchingRetrySettings {
    if (interval.compareTo(Duration.ofMinutes(1)) < 0) {
      throw new IllegalArgumentException(
          "rainmaker.matching-ranking.retry.interval must be at least 1m: " + interval);
    }
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "rainmaker.matching-ranking.retry.batch-size must be from 1 to "
              + MAX_BATCH_SIZE
              + ": "
              + batchSize);
    }
  }
}
