package se.rainmaker.contentingestion.ingestion;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.feed.Feed;
import se.rainmaker.contentingestion.feed.FeedItem;
import se.rainmaker.contentingestion.feed.InvalidFeedException;
import se.rainmaker.contentingestion.feed.RssFeedParser;
import se.rainmaker.contentingestion.ingestion.ArticleVersions.LatestVersion;
import se.rainmaker.contentingestion.page.ArticlePage;
import se.rainmaker.contentingestion.page.ArticlePageParser;
import se.rainmaker.contentingestion.page.InvalidArticlePageException;
import se.rainmaker.contentingestion.site.PublisherSite;
import se.rainmaker.contentingestion.site.SiteUnavailableException;

/**
 * One poll of the publisher's feed (#147): stores every new article and every newer version, and
 * announces each with {@link ArticleStored}.
 *
 * <ul>
 *   <li>An article whose update time is not newer than the stored one is skipped without reading
 *       its page, so a repeated item is never stored twice.
 *   <li>A feed that is down or invalid is logged; the next poll tries again. One article page that
 *       fails only skips that article.
 *   <li>Pages are only read from the feed's own site, so a feed can never make ingestion call
 *       another address.
 * </ul>
 */
public class FeedIngestion {

  private static final Logger LOG = LoggerFactory.getLogger(FeedIngestion.class);
  private static final String CORRELATION_ID = "correlation_id";

  private final URI feedUrl;
  private final PublisherSite site;
  private final RssFeedParser feedParser;
  private final ArticlePageParser pageParser;
  private final ArticleVersions versions;
  private final Consumer<ArticleStored> announcer;

  FeedIngestion(
      URI feedUrl,
      PublisherSite site,
      RssFeedParser feedParser,
      ArticlePageParser pageParser,
      ArticleVersions versions,
      Consumer<ArticleStored> announcer) {
    this.feedUrl = Objects.requireNonNull(feedUrl);
    this.site = site;
    this.feedParser = feedParser;
    this.pageParser = pageParser;
    this.versions = versions;
    this.announcer = announcer;
  }

  /** Reads the feed once. Never throws for a site that is down or sends something invalid. */
  public void pollOnce() {
    Feed feed;
    try {
      feed = feedParser.parse(site.read(feedUrl));
    } catch (SiteUnavailableException | InvalidFeedException unreadable) {
      LOG.warn("Could not read the feed {}: {}", feedUrl, unreadable.getMessage());
      return;
    }
    for (FeedItem item : feed.items()) {
      try {
        ingest(item, feed.language());
      } catch (SiteUnavailableException | InvalidArticlePageException failed) {
        LOG.warn("Skipped {} until the next poll: {}", item.url(), failed.getMessage());
      }
    }
  }

  private void ingest(FeedItem item, String language) {
    Optional<LatestVersion> latest = versions.latest(item.url());
    if (latest.isPresent() && !item.lastModified().isAfter(latest.get().lastModified())) {
      return;
    }
    URI page = URI.create(item.url());
    if (!sameSite(page)) {
      LOG.warn("Skipped {}: not on the feed's site {}", item.url(), feedUrl);
      return;
    }
    ArticlePage article = pageParser.parse(site.read(page), item.url());
    ArticleStored stored =
        new ArticleStored(
            latest.map(LatestVersion::articleId).orElseGet(UUID::randomUUID),
            latest.map(version -> version.version() + 1).orElse(1),
            item.url(),
            item.headline(),
            article.text(),
            article.section(),
            language,
            item.publishedAt(),
            item.lastModified().equals(item.publishedAt()) ? null : item.lastModified(),
            UUID.randomUUID().toString());
    MDC.put(CORRELATION_ID, stored.correlationId());
    try {
      if (versions.insert(stored, item.externalId(), item.lastModified())) {
        LOG.info("Stored version {} of {}", stored.version(), stored.url());
        announcer.accept(stored);
      }
    } finally {
      MDC.remove(CORRELATION_ID);
    }
  }

  private boolean sameSite(URI page) {
    return Objects.equals(page.getScheme(), feedUrl.getScheme())
        && Objects.equals(page.getHost(), feedUrl.getHost())
        && page.getPort() == feedUrl.getPort();
  }
}
