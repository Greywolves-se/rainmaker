package se.rainmaker.contentingestion.api;

import java.time.Instant;
import java.util.UUID;

/**
 * A new article, or a new version of one, has been stored (contract section 6.1). Published as a
 * Spring event inside G2; content-intent classifies it. It carries the text, so content-intent
 * never reads this module's database.
 *
 * <p>{@code text} is the body without the headline. It stays inside G2: never logged, never in a
 * REST answer.
 *
 * @param articleId Rainmaker's id for the article, the same for every version
 * @param version 1 for a new article, one more for each newer update time on the same URL
 * @param url the canonical URL, exactly as the feed gives it (decision D-2)
 * @param updatedAt when the site last changed it, or {@code null} if it was never updated
 * @param correlationId follows this version through classification and matching
 */
public record ArticleStored(
    UUID articleId,
    int version,
    String url,
    String headline,
    String text,
    String section,
    String language,
    Instant publishedAt,
    Instant updatedAt,
    String correlationId) {

  /** Leaves out the body text, so the event can never put it in a log line. */
  @Override
  public String toString() {
    return "ArticleStored[articleId=%s, version=%d, url=%s, correlationId=%s]"
        .formatted(articleId, version, url, correlationId);
  }
}
