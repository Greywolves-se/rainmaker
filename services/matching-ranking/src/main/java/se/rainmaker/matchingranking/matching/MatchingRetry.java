package se.rainmaker.matchingranking.matching;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import se.rainmaker.matchingranking.offers.G3OffersClient;
import se.rainmaker.matchingranking.offers.OffersUnavailableException;
import se.rainmaker.matchingranking.ranking.OfferForMatching;
import se.rainmaker.matchingranking.ranking.OfferRanking;
import se.rainmaker.matchingranking.ranking.RankedOffer;

/**
 * Matches green article versions again that couldn't be matched because G3's offers API was down
 * (#248). Without this, such a version stays without suggestions until the article is edited:
 * content-intent classifies a green version only once.
 *
 * <p>Each run takes at most {@code batch-size} waiting versions not tried for {@code interval},
 * oldest attempt first, asks G3 for its offers once, and ranks each version with the topics it was
 * classified with, as the first match would have. If G3 is still down, they wait for the next run.
 */
class MatchingRetry implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(MatchingRetry.class);

  private final G3OffersClient offers;
  private final SuggestionStore store;
  private final MatchingRetrySettings settings;

  MatchingRetry(G3OffersClient offers, SuggestionStore store, MatchingRetrySettings settings) {
    this.offers = offers;
    this.store = store;
    this.settings = settings;
  }

  /** One run. Any error is logged; the versions it took are tried again after the interval. */
  @Override
  public void run() {
    try {
      List<WaitingVersion> due = store.takeForRetry(settings.batchSize(), settings.interval());
      if (due.isEmpty()) {
        return;
      }
      List<OfferForMatching> current;
      try {
        current = offers.offers();
      } catch (OffersUnavailableException exception) {
        LOG.warn(
            "G3's offers API is still unavailable: {}. {} articles wait for the next run",
            exception.getMessage(),
            due.size());
        return;
      }
      LOG.info("Matching {} articles again now that G3 answers", due.size());
      due.forEach(version -> matchAgain(version, current));
    } catch (RuntimeException error) {
      LOG.error("Matching articles again failed; the next run tries again", error);
    }
  }

  /**
   * One article. An error is logged and the run goes on with the next one; this one was marked as
   * tried when the run took it, so it is tried again after the interval. The error line carries the
   * article's correlation id too: the catch is inside the MDC's scope.
   */
  private void matchAgain(WaitingVersion version, List<OfferForMatching> current) {
    try (MDC.MDCCloseable correlation =
        MDC.putCloseable("correlation_id", version.correlationId())) {
      try {
        rankAndSave(version, current);
      } catch (RuntimeException error) {
        LOG.error(
            "Matching article {} version {} again failed; it is tried again after the interval",
            version.articleId(),
            version.version(),
            error);
      }
    }
  }

  private void rankAndSave(WaitingVersion version, List<OfferForMatching> current) {
    List<RankedOffer> ranked = OfferRanking.rank(version.topics(), current);
    if (store.save(version.articleId(), version.version(), ranked)) {
      LOG.info(
          "Article {} version {}: {} suggestions (matched again)",
          version.articleId(),
          version.version(),
          ranked.size());
    } else {
      LOG.info(
          "Article {} version {} was replaced by a newer one; not matched again",
          version.articleId(),
          version.version());
    }
  }
}
