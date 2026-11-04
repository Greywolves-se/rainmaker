package se.rainmaker.contentintent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** A wrong retry setting stops G2 at start-up (#223, #258). */
class RetrySettingsTest {

  private static final Duration ONE_DAY = Duration.ofDays(1);

  @Test
  void acceptsTheLimits() {
    assertThat(new RetrySettings(Duration.ofMinutes(1), ONE_DAY, 1, true).batchSize()).isEqualTo(1);
    assertThat(new RetrySettings(Duration.ofMinutes(5), ONE_DAY, 100, true).batchSize())
        .isEqualTo(100);
  }

  @Test
  void refusesIntervalShorterThanOneClassification() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetrySettings(Duration.ofSeconds(59), ONE_DAY, 10, true))
        .withMessageContaining("interval must be at least 1m");
  }

  @Test
  void refusesBatchSizeOutsideOneToHundred() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetrySettings(Duration.ofMinutes(5), ONE_DAY, 0, true))
        .withMessageContaining("batch-size must be from 1 to 100");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetrySettings(Duration.ofMinutes(5), ONE_DAY, 101, true))
        .withMessageContaining("batch-size must be from 1 to 100");
  }

  /** The longest wait may equal the first one: then the wait never grows. */
  @Test
  void acceptsMaxWaitEqualToInterval() {
    assertThat(new RetrySettings(Duration.ofMinutes(5), Duration.ofMinutes(5), 10, true).maxWait())
        .isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void refusesMaxWaitShorterThanInterval() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetrySettings(Duration.ofMinutes(5), Duration.ofMinutes(4), 10, true))
        .withMessageContaining("max-wait must be at least the interval");
  }
}
