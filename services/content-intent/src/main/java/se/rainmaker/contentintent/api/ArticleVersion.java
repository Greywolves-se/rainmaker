package se.rainmaker.contentintent.api;

import java.util.UUID;

/**
 * One version of one article, the key of a classification (#209, contract 3.2).
 *
 * @param articleId Rainmaker's id for the article, the same for every version
 * @param version the version (1 for the first)
 */
public record ArticleVersion(UUID articleId, int version) {

  /** Checks that both parts are present. */
  public ArticleVersion {
    if (articleId == null) {
      throw new IllegalArgumentException("articleId is required");
    }
    if (version < 1) {
      throw new IllegalArgumentException("version starts at 1: " + version);
    }
  }
}
