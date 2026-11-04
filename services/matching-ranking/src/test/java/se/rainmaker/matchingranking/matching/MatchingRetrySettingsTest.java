package se.rainmaker.matchingranking.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** A wrong retry setting stops G2 at start-up (#248). */
class MatchingRetrySettingsTest {

  @Test
  void acceptsTheLimits() {
    assertThat(new MatchingRetrySettings(Duration.ofMinutes(1), 1, true).batchSize()).isEqualTo(1);
    assertThat(new MatchingRetrySettings(Duration.ofMinutes(5), 100, true).batchSize())
        .isEqualTo(100);
  }

  @Test
  void refusesIntervalShorterThanOneMinute() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MatchingRetrySettings(Duration.ofSeconds(59), 10, true))
        .withMessageContaining("rainmaker.matching-ranking.retry.interval must be at least 1m");
  }

  @Test
  void refusesBatchSizeOutsideOneToHundred() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MatchingRetrySettings(Duration.ofMinutes(5), 0, true))
        .withMessageContaining("batch-size must be from 1 to 100");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MatchingRetrySettings(Duration.ofMinutes(5), 101, true))
        .withMessageContaining("batch-size must be from 1 to 100");
  }
}
