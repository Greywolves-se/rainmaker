package se.rainmaker.widgetdelivery.links;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A wrong setting stops G1 at start-up, not on the first hover (#151), including timeouts the
 * reader would never wait for (#224).
 */
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

  /** G1's own settings: 2 x (250 ms + 500 ms) + 50 ms. */
  @Test
  void demoSettingsFitTheReaderWait() {
    ApprovedLinksProperties demo =
        new ApprovedLinksProperties(
            URI.create("http://localhost:8083"),
            Duration.ofMillis(250),
            Duration.ofMillis(500),
            2,
            Duration.ofMillis(50),
            BREAKER);

    assertThat(demo.worstCaseWait()).isEqualTo(Duration.ofMillis(1550));
    assertThatCode(demo::requireWithinReaderWait).doesNotThrowAnyException();
  }

  /** 3 x (250 ms + 600 ms) + 50 ms + 100 ms: rainmaker.js has given up after 2 s. */
  @Test
  void refusesSettingsThatOutlastTheReaderWait() {
    ApprovedLinksProperties slow = settings(URI.create("http://localhost:8083"), 3, BREAKER);

    assertThat(slow.worstCaseWait()).isGreaterThanOrEqualTo(ApprovedLinksProperties.READER_WAIT);
    assertThatThrownBy(slow::requireWithinReaderWait)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2000 ms");
  }

  /** The back-off doubles per retry; a huge number of attempts must not overflow. */
  @Test
  void refusesHugeNumberOfAttempts() {
    ApprovedLinksProperties endless =
        settings(URI.create("http://localhost:8083"), Integer.MAX_VALUE, BREAKER);

    assertThatThrownBy(endless::requireWithinReaderWait)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reader");
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
