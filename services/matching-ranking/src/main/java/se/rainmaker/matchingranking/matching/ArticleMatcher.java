package se.rainmaker.matchingranking.matching;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import se.rainmaker.contentintent.api.ArticleClassified;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.matchingranking.offers.G3OffersClient;
import se.rainmaker.matchingranking.offers.OffersUnavailableException;
import se.rainmaker.matchingranking.ranking.OfferRanking;
import se.rainmaker.matchingranking.ranking.RankedOffer;

/**
 * Matches each classified article version to G3's offers (#149; contract sections 4 and 6.2).
 *
 * <ul>
 *   <li><b>Not green</b> (yellow, red, failed, unclassified): the article's suggestions are
 *       withdrawn at once, without asking G3, so brand safety never waits for another app (D-4).
 *   <li><b>Green:</b> the offers are read from G3 and ranked ({@link OfferRanking}).
 *   <li><b>Green, but G3 can't be reached</b> after the retries: nothing new is stored and the
 *       error is logged. A new version still loses its old set (fail closed). The article is
 *       matched again the next time it is classified.
 * </ul>
 *
 * <p>Runs after content-intent's transaction commits, or at once if it was published outside one.
 * Handling the same event twice changes nothing ({@link SuggestionStore}).
 */
@Component
class ArticleMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(ArticleMatcher.class);

  private final G3OffersClient offers;
  private final SuggestionStore store;

  ArticleMatcher(G3OffersClient offers, SuggestionStore store) {
    this.offers = offers;
    this.store = store;
  }

  @TransactionalEventListener(fallbackExecution = true)
  void onArticleClassified(ArticleClassified event) {
    try (MDC.MDCCloseable correlation = MDC.putCloseable("correlation_id", event.correlationId())) {
      Classification classification = event.classification();
      if (!classification.mayGetSuggestions()) {
        save(event, List.of());
        return;
      }
      List<RankedOffer> ranked;
      try {
        ranked = OfferRanking.rank(classification.topics(), offers.offers());
      } catch (OffersUnavailableException exception) {
        LOG.error(
            "No suggestions for article {} version {} for now: {}. It is matched again when it is"
                + " classified again.",
            event.articleId(),
            event.articleVersion(),
            exception.getMessage(),
            exception);
        store.withdrawIfNewer(event.articleId(), event.articleVersion());
        return;
      }
      save(event, ranked);
    }
  }

  private void save(ArticleClassified event, List<RankedOffer> ranked) {
    if (store.save(event.articleId(), event.articleVersion(), ranked)) {
      LOG.info(
          "Article {} version {}: {} suggestions",
          event.articleId(),
          event.articleVersion(),
          ranked.size());
    } else {
      LOG.info(
          "Article {} version {} is older than the newest one matched; ignored",
          event.articleId(),
          event.articleVersion());
    }
  }
}
