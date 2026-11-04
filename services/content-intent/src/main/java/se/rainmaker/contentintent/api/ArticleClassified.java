package se.rainmaker.contentintent.api;

import java.util.UUID;

/**
 * One article version has been classified (#145, contract 6.2). Published as a Spring event inside
 * G2 once the classification is stored; matching-ranking listens. Also published when the
 * classification failed or was deferred: those are treated like red (decision D-4).
 *
 * <p>Defined in #149 so matching could be built; content-intent publishes it (#148).
 *
 * @param articleId Rainmaker's id for the article, the same for every version
 * @param articleVersion the version that was classified (1 for the first)
 * @param url the article's canonical URL (decision D-2)
 * @param headline the headline, so the editor view can list the article
 * @param classification the result, whatever its status
 * @param correlationId follows this article version through classification and matching
 */
public record ArticleClassified(
    UUID articleId,
    int articleVersion,
    String url,
    String headline,
    Classification classification,
    String correlationId) {

  /** Checks the fields that matching relies on. */
  public ArticleClassified {
    if (articleId == null) {
      throw new IllegalArgumentException("articleId is required");
    }
    if (articleVersion < 1) {
      throw new IllegalArgumentException("articleVersion starts at 1: " + articleVersion);
    }
    if (classification == null) {
      throw new IllegalArgumentException("classification is required");
    }
  }
}
