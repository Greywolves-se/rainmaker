package se.rainmaker.contentintent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** A wrong retry setting stops G2 at start-up (#223). */
class RetrySettingsTest {

  @Test
  void acceptsTheLimits() {
    assertThat(new RetrySettings(Duration.ofMinutes(1), 1, true).batchSize()).isEqualTo(1);
    assertThat(new RetrySettings(Duration.ofMinutes(5), 100, true).batchSize()).isEqualTo(100);
  }

  @Test
  void refusesIntervalShorterThanOneClassification() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetrySettings(Duration.ofSeconds(59), 10, true))
        .withMessageContaining("interval must be at least 1m");
  }

  @Test
  void refusesBatchSizeOutsideOneToHundred() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetrySettings(Duration.ofMinutes(5), 0, true))
        .withMessageContaining("batch-size must be from 1 to 100");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetrySettings(Duration.ofMinutes(5), 101, true))
        .withMessageContaining("batch-size must be from 1 to 100");
  }
}
