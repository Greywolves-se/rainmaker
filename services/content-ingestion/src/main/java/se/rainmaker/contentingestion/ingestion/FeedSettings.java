package se.rainmaker.contentingestion.ingestion;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where and how often ingestion reads the publisher's feed ({@code
 * rainmaker.content-ingestion.feed.*}).
 *
 * @param url the RSS feed; in the demo the test site's
 * @param pollInterval time between the end of one poll and the start of the next, at least 1 s
 * @param timeout connect and read timeout for every call to the site
 * @param polling {@code false} turns the schedule off, e.g. in tests that start the whole app
 */
@ConfigurationProperties("rainmaker.content-ingestion.feed")
record FeedSettings(
    @DefaultValue("http://localhost:3000/rss.xml") URI url,
    @DefaultValue("1m") Duration pollInterval,
    @DefaultValue("5s") Duration timeout,
    @DefaultValue("true") boolean polling) {

  private static final Duration MIN_POLL_INTERVAL = Duration.ofSeconds(1);

  /**
   * Stops start-up on a poll interval under 1 s, e.g. a typo in {@code
   * RAINMAKER_FEED_POLL_INTERVAL}: ingestion must never read the publisher's site in a tight loop.
   */
  FeedSettings {
    if (pollInterval.compareTo(MIN_POLL_INTERVAL) < 0) {
      throw new IllegalArgumentException(
          "rainmaker.content-ingestion.feed.poll-interval must be at least 1s: " + pollInterval);
    }
  }
}
