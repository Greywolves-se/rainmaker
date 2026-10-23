package se.rainmaker.contentintent.api;

import java.util.UUID;

/**
 * What the classifier sees of an article: only the headline and the text, never anything about
 * readers (#145, section 8).
 *
 * @param articleId Rainmaker's id of the article
 * @param version the article version this classification is for (1 for the first)
 * @param headline the headline
 * @param text the body text
 */
public record ArticleToClassify(UUID articleId, int version, String headline, String text) {

  /** Checks that every field is present. */
  public ArticleToClassify {
    if (articleId == null) {
      throw new IllegalArgumentException("articleId is required");
    }
    if (version < 1) {
      throw new IllegalArgumentException("version starts at 1: " + version);
    }
    if (headline == null || headline.isBlank() || text == null || text.isBlank()) {
      throw new IllegalArgumentException("headline and text are required");
    }
  }
}
