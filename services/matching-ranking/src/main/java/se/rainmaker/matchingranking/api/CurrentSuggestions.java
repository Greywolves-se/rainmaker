package se.rainmaker.matchingranking.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  /**
   * The complete current set of each article, as {@link #forArticle}, read in one query (#202: G3
   * polls 100 articles every few seconds).
   *
   * @return one entry per distinct article asked for, in the order asked; an empty list when it has
   *     no suggestions
   */
  Map<UUID, List<Suggestion>> forArticles(Collection<UUID> articleIds);
}
