package se.rainmaker.contentingestion.feed;

import java.time.Instant;

/**
 * One article in the publisher's feed.
 *
 * @param url the item's {@code <link>}: the article's key (decision D-2)
 * @param externalId the site's own id ({@code <guid>}), for debugging only; may be {@code null}
 * @param lastModified {@code <atom:updated>}, or the publish time when the item has none
 */
public record FeedItem(
    String url, String externalId, String headline, Instant publishedAt, Instant lastModified) {}
