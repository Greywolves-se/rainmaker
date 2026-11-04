package se.rainmaker.matchingranking.api;

import java.util.List;
import java.util.UUID;

/**
 * An offer suggested for one article version (#145, contract 3.5). The editor's decision (approved,
 * rejected, withdrawn) is kept in G3, not here.
 *
 * @param suggestionId the suggestion's id (UUIDv7)
 * @param articleId the article
 * @param articleVersion the article version it was matched for
 * @param offerId the offer in G3
 * @param rank 1 for the best fit, at most 3
 * @param matchedTopics why the offer fits: the article's topic keys it matched, e.g. {@code
 *     ["travel"]}
 */
public record Suggestion(
    UUID suggestionId,
    UUID articleId,
    int articleVersion,
    UUID offerId,
    int rank,
    List<String> matchedTopics) {

  /** Makes the list unmodifiable. */
  public Suggestion {
    matchedTopics = List.copyOf(matchedTopics);
  }
}
