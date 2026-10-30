package se.rainmaker.g2contentapp.articles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import se.rainmaker.contentintent.api.Reason;

/**
 * The articles API's answer (contract 7.1), for {@code ?recent=…} and {@code ?url=…} alike. After
 * the demo, each event to G3 carries one {@link Article} exactly like these (ADR 0004).
 *
 * @param articles newest stored first; empty for an unknown URL
 */
record ArticleList(List<Article> articles) {

  /** Copies the list, so the answer cannot change afterwards. */
  ArticleList {
    articles = List.copyOf(articles);
  }

  /**
   * The latest version of one article, without its body text.
   *
   * @param suggestions the complete current set for this version; empty means "none", so G3
   *     withdraws the earlier ones (D-5)
   */
  record Article(
      UUID articleId,
      String externalId,
      int version,
      String url,
      String headline,
      String section,
      String language,
      Instant publishedAt,
      Instant updatedAt,
      ArticleClassification classification,
      List<ArticleSuggestion> suggestions,
      String correlationId) {

    /** Copies the list, so the answer cannot change afterwards. */
    Article {
      suggestions = List.copyOf(suggestions);
    }
  }

  /**
   * The version's current classification (contract 3.2), with the keys as JSON strings.
   *
   * @param zone {@code null} unless {@code status} is {@code classified}
   */
  record ArticleClassification(
      String status,
      String zone,
      List<String> topics,
      Reason reason,
      String model,
      Instant classifiedAt) {}

  /** One suggestion of the set (contract 3.5); the editor's decision is kept in G3. */
  record ArticleSuggestion(UUID suggestionId, UUID offerId, int rank, List<String> matchedTopics) {}
}
