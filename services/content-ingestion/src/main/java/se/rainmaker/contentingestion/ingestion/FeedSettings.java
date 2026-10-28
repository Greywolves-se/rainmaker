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
 * @param pollInterval time between the end of one poll and the start of the next
 * @param timeout connect and read timeout for every call to the site
 * @param polling {@code false} turns the schedule off, e.g. in tests that start the whole app
 */
@ConfigurationProperties("rainmaker.content-ingestion.feed")
record FeedSettings(
    @DefaultValue("http://localhost:3000/rss.xml") URI url,
    @DefaultValue("1m") Duration pollInterval,
    @DefaultValue("5s") Duration timeout,
    @DefaultValue("true") boolean polling) {}
