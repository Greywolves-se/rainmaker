package se.rainmaker.matchingranking.ranking;

import java.util.UUID;

/**
 * What matching needs of one of G3's offers (contract 7.2): the rest stays in G3.
 *
 * @param offerId the offer's id
 * @param category its commercial category key, e.g. {@code "travel"}; may be unknown to G2
 * @param cpcOre its price per click in whole öre; only breaks ties
 */
public record OfferForMatching(UUID offerId, String category, int cpcOre) {}
