package se.rainmaker.contentintent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** A wrong retry setting stops G2 at start-up instead of misbehaving later (#223). */
class RetryPropertiesTest {

  @Test
  void acceptsPositiveIntervalAndBatchSizeUpToHundred() {
    assertThat(new RetryProperties(Duration.ofMinutes(5), 100).batchSize()).isEqualTo(100);
  }

  @Test
  void refusesIntervalThatIsNotPositive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetryProperties(Duration.ZERO, 10))
        .withMessageContaining("retry.interval must be positive");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetryProperties(null, 10))
        .withMessageContaining("retry.interval must be positive");
  }

  @Test
  void refusesBatchSizeOutsideOneToHundred() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetryProperties(Duration.ofMinutes(5), 0))
        .withMessageContaining("retry.batch-size must be 1-100");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetryProperties(Duration.ofMinutes(5), 101))
        .withMessageContaining("retry.batch-size must be 1-100");
  }
}
