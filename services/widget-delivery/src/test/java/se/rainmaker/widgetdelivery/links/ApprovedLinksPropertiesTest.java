package se.rainmaker.widgetdelivery.links;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A wrong setting stops G1 at start-up, not on the first hover (#151). */
class ApprovedLinksPropertiesTest {

  private static final ApprovedLinksProperties.CircuitBreaker BREAKER =
      new ApprovedLinksProperties.CircuitBreaker(10, 5, 50, Duration.ofSeconds(10));

  @Test
  void refusesAddressWithoutHost() {
    assertThatThrownBy(() -> settings(URI.create("localhost:8083"), 2, BREAKER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("base-url");
  }

  @Test
  void refusesFewerThanOneAttempt() {
    assertThatThrownBy(() -> settings(URI.create("http://localhost:8083"), 0, BREAKER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max-attempts");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 101})
  void refusesFailureRateOutsideOneToHundred(int percent) {
    assertThatThrownBy(
            () ->
                new ApprovedLinksProperties.CircuitBreaker(10, 5, percent, Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("failure-rate-percent");
  }

  @Test
  void refusesMissingCircuitBreakerSettings() {
    assertThatThrownBy(() -> settings(URI.create("http://localhost:8083"), 2, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("circuit-breaker");
  }

  private static ApprovedLinksProperties settings(
      URI baseUrl, int maxAttempts, ApprovedLinksProperties.CircuitBreaker breaker) {
    return new ApprovedLinksProperties(
        baseUrl,
        Duration.ofMillis(250),
        Duration.ofMillis(600),
        maxAttempts,
        Duration.ofMillis(50),
        breaker);
  }
}
