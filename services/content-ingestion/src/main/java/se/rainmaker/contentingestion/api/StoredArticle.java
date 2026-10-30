package se.rainmaker.contentingestion.api;

import java.time.Instant;
import java.util.UUID;

/**
 * The latest stored version of one article, without its body text (contract 3.1 and 7.1): the text
 * stays inside G2's ingestion and classification, never in a REST answer.
 *
 * @param articleId Rainmaker's id for the article, the same for every version
 * @param externalId the publisher's own id for it, or {@code null} if the feed has none
 * @param version the latest stored version (1 for the first)
 * @param url the canonical URL, exactly as the feed gives it (decision D-2)
 * @param updatedAt when the site last changed it, or {@code null} if it was never updated
 * @param correlationId follows this version through classification and matching
 */
public record StoredArticle(
    UUID articleId,
    String externalId,
    int version,
    String url,
    String headline,
    String section,
    String language,
    Instant publishedAt,
    Instant updatedAt,
    String correlationId) {}
