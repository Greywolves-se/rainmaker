package se.rainmaker.contentingestion.api;

import java.util.List;
import java.util.Optional;

/**
 * The latest stored version of each article, for modules inside G2 (#202; the articles API,
 * contract 7.1, which answers G3's poll of 100 articles every few seconds).
 */
public interface LatestArticles {

  /**
   * The latest version of the {@code count} articles whose latest version was stored most recently,
   * newest first, so an edited article comes back to the top. Read in one query.
   */
  List<StoredArticle> recent(int count);

  /** The latest version of the article with this exact URL, or empty if none is stored. */
  Optional<StoredArticle> byUrl(String url);
}
