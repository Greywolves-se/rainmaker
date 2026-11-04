package se.rainmaker.matchingranking.ranking;

import java.util.UUID;
import se.rainmaker.contentintent.api.Topic;

/**
 * An offer chosen for an article, in its place.
 *
 * @param offerId the offer
 * @param rank 1 for the best fit
 * @param matchedTopic the article's topic it fits
 */
public record RankedOffer(UUID offerId, int rank, Topic matchedTopic) {}
