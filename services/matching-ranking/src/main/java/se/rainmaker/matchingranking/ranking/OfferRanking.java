package se.rainmaker.matchingranking.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.rainmaker.contentintent.api.Topic;

/**
 * Picks the offers for a green article (#149; contract section 4).
 *
 * <ul>
 *   <li>An offer fits when its category is one of the article's topics. Claude lists the topics
 *       most relevant first, so an offer for the first topic fits better than one for the second.
 *   <li>Better fit first, whatever the CPC. <b>CPC only breaks ties</b> (product rule), then the
 *       offer id, so the order never changes between runs.
 *   <li>At most {@value #MAX_SUGGESTIONS}. {@code other} never matches.
 *   <li>An offer whose category G2 doesn't know is skipped and logged, not fatal: the keys are kept
 *       in both G3 and G2, so they can drift apart.
 * </ul>
 */
public final class OfferRanking {

  /** Most suggestions per article (contract section 4). */
  public static final int MAX_SUGGESTIONS = 3;

  private static final Logger LOG = LoggerFactory.getLogger(OfferRanking.class);

  private OfferRanking() {}

  /** The best offers for an article with these topics (most relevant first), ranked from 1. */
  public static List<RankedOffer> rank(List<Topic> topics, List<OfferForMatching> offers) {
    List<Candidate> candidates = new ArrayList<>();
    for (OfferForMatching offer : offers) {
      Optional<Topic> category = knownCategory(offer);
      if (category.isPresent() && category.get() != Topic.OTHER) {
        int fit = topics.indexOf(category.get());
        if (fit >= 0) {
          candidates.add(new Candidate(offer, category.get(), fit));
        }
      }
    }
    candidates.sort(
        Comparator.comparingInt(Candidate::fit)
            .thenComparing(
                Comparator.comparingInt((Candidate candidate) -> candidate.offer().cpcOre())
                    .reversed())
            .thenComparing(candidate -> candidate.offer().offerId()));
    List<Candidate> best = candidates.subList(0, Math.min(MAX_SUGGESTIONS, candidates.size()));
    return IntStream.range(0, best.size())
        .mapToObj(
            index ->
                new RankedOffer(
                    best.get(index).offer().offerId(), index + 1, best.get(index).topic()))
        .toList();
  }

  private static Optional<Topic> knownCategory(OfferForMatching offer) {
    Optional<Topic> topic =
        offer.category() == null ? Optional.empty() : Topic.fromKey(offer.category());
    if (topic.isEmpty() || offer.offerId() == null) {
      LOG.warn(
          "Skipping offer {} with category \"{}\": no id, or the category isn't a known topic",
          offer.offerId(),
          offer.category());
      return Optional.empty();
    }
    return topic;
  }

  /** An offer that fits, with how well: 0 for the article's first topic, 1 for the second... */
  private record Candidate(OfferForMatching offer, Topic topic, int fit) {}
}
