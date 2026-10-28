package se.rainmaker.publishercontrol.articles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One answer of G2's articles API (contract 7.1), {@code ?recent=…} or {@code ?url=…}, with the
 * fields G3 uses; the others are ignored. A missing required field makes the whole answer unusable,
 * so nothing half-read is stored.
 *
 * <p>After the demo, events replace the polling (ADR 0004): each event carries one {@link
 * G2Article}, so G3 handles it like one article of this list.
 *
 * @param articles the articles, newest stored first
 */
record ArticleList(List<G2Article> articles) {

  ArticleList {
    require(articles != null, "articles");
  }

  /** The latest version of one article, with its classification and complete suggestion set. */
  record G2Article(
      UUID articleId,
      int version,
      String url,
      String headline,
      Instant publishedAt,
      Instant updatedAt,
      Classification classification,
      List<SuggestedOffer> suggestions,
      String correlationId) {

    G2Article {
      require(articleId != null, "articleId");
      require(version >= 1, "version");
      require(url != null && !url.isBlank(), "url");
      require(headline != null, "headline");
      require(publishedAt != null, "publishedAt");
      require(classification != null, "classification");
      require(suggestions != null, "suggestions");
      require(correlationId != null && !correlationId.isBlank(), "correlationId");
    }

    /** Only a classified, green article may have suggestions (contract 3.3, D-4). */
    boolean isGreen() {
      return "classified".equals(classification.status()) && "green".equals(classification.zone());
    }
  }

  /** The zone ({@code null} unless classified) and Claude's reason in both languages. */
  record Classification(String status, String zone, Reason reason) {

    Classification {
      boolean classified = "classified".equals(status);
      require(
          classified || "unclassified".equals(status) || "failed".equals(status),
          "classification.status");
      require(
          classified
              ? "green".equals(zone) || "yellow".equals(zone) || "red".equals(zone)
              : zone == null,
          "classification.zone");
    }
  }

  /** Claude's reason for the zone, in Swedish and English (D-9). */
  record Reason(String sv, String en) {}

  /** One suggestion of the set. */
  record SuggestedOffer(UUID suggestionId, UUID offerId, int rank, List<String> matchedTopics) {

    SuggestedOffer {
      require(suggestionId != null, "suggestions.suggestionId");
      require(offerId != null, "suggestions.offerId");
      require(rank >= 1 && rank <= 3, "suggestions.rank");
      require(matchedTopics != null, "suggestions.matchedTopics");
    }
  }

  private static void require(boolean condition, String field) {
    if (!condition) {
      throw new IllegalArgumentException("missing or invalid " + field);
    }
  }
}
