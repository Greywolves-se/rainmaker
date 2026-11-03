package se.rainmaker.contentingestion.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The poll interval can be set from the environment (#154), so a typo must stop start-up. */
class FeedSettingsTest {

  private static final URI FEED = URI.create("http://localhost:3000/rss.xml");

  @Test
  void acceptsOneSecond() {
    FeedSettings settings = settings(Duration.ofSeconds(1));

    assertThat(settings.pollInterval()).isEqualTo(Duration.ofSeconds(1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "PT-5S", "PT0.5S"})
  void rejectsPollIntervalBelowOneSecond(String interval) {
    assertThatThrownBy(() -> settings(Duration.parse(interval)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("poll-interval");
  }

  private static FeedSettings settings(Duration pollInterval) {
    return new FeedSettings(FEED, pollInterval, Duration.ofSeconds(5), true);
  }
}
