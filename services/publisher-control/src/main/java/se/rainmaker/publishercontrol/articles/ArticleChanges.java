package se.rainmaker.publishercontrol.articles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One answer of G2's articles API (contract 7.1), with the fields G3 uses; the others are ignored.
 * A missing required field makes the whole answer unusable, so nothing half-read is stored.
 *
 * @param articles the changed articles, oldest change first
 * @param nextSince where the next poll starts
 */
record ArticleChanges(List<ChangedArticle> articles, Instant nextSince) {

  ArticleChanges {
    require(articles != null, "articles");
    require(nextSince != null, "nextSince");
  }

  /** The latest version of one article, with its classification and complete suggestion set. */
  record ChangedArticle(
      UUID articleId,
      int version,
      String url,
      String headline,
      Instant publishedAt,
      Classification classification,
      List<SuggestedOffer> suggestions,
      Instant changedAt,
      String correlationId) {

    ChangedArticle {
      require(articleId != null, "articleId");
      require(version >= 1, "version");
      require(url != null && !url.isBlank(), "url");
      require(headline != null, "headline");
      require(classification != null, "classification");
      require(suggestions != null, "suggestions");
      require(changedAt != null, "changedAt");
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
