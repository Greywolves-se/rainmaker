package se.rainmaker.publishercontrol.articles;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The reader call to G2 must end before G1 gives up on G3 (#230); G3 checks it at start-up. */
class ArticlesPropertiesTest {

  private static final ArticlesProperties.CircuitBreaker BREAKER =
      new ArticlesProperties.CircuitBreaker(10, 5, 50, Duration.ofSeconds(10));

  @Test
  void demoSettingsStayUnderG1sWait() {
    ArticlesProperties demo = settings(Duration.ofMillis(100), Duration.ofMillis(350));

    assertThatCode(demo::requireReaderWithinG1Wait).doesNotThrowAnyException();
  }

  @Test
  void readerCallAsLongAsG1sWaitIsRefused() {
    ArticlesProperties slow = settings(Duration.ofMillis(100), Duration.ofMillis(400));

    assertThatIllegalArgumentException()
        .isThrownBy(slow::requireReaderWithinG1Wait)
        .withMessageContaining("must stay under G1's 500 ms");
  }

  @Test
  void refusesReaderWithoutTimeoutOrCircuitBreaker() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ArticlesProperties.Reader(Duration.ZERO, Duration.ofMillis(350), BREAKER))
        .withMessageContaining("reader.connect-timeout must be positive");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ArticlesProperties.Reader(Duration.ofMillis(100), Duration.ofMillis(350), null))
        .withMessageContaining("reader.circuit-breaker.* must be set");
  }

  @Test
  void refusesFailureRateOutsideOneToHundred() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ArticlesProperties.CircuitBreaker(10, 5, 0, Duration.ofSeconds(10)))
        .withMessageContaining("failure-rate-percent must be from 1 to 100");
  }

  private static ArticlesProperties settings(Duration connectTimeout, Duration readTimeout) {
    return new ArticlesProperties(
        URI.create("http://localhost:8082"),
        Duration.ofSeconds(2),
        Duration.ofSeconds(5),
        3,
        Duration.ofMillis(500),
        Duration.ofSeconds(5),
        100,
        new ArticlesProperties.Reader(connectTimeout, readTimeout, BREAKER),
        true);
  }
}
