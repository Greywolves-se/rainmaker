package se.rainmaker.matchingranking.api;

import java.util.List;
import java.util.UUID;

/**
 * The current suggestions per article, for modules inside G2 (e.g. the articles API, contract 7.1).
 * G3 gets them over REST.
 */
public interface CurrentSuggestions {

  /**
   * The complete current set for the article's newest matched version, best fit first. Empty when
   * the article isn't green, has no fitting offer, or hasn't been matched.
   */
  List<Suggestion> forArticle(UUID articleId);
}
