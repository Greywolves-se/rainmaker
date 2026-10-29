package se.rainmaker.contentintent.pipeline;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentintent.api.ArticleClassifier;
import se.rainmaker.contentintent.api.ArticleToClassify;
import se.rainmaker.contentintent.api.Classification;

/**
 * Classifies every stored article version (#209; contract 6.1, 3.2 and 6.2).
 *
 * <ol>
 *   <li>The version is stored as unclassified at once. A version that is stored already is a
 *       repeated event, and nothing changes.
 *   <li>The classifier is called outside any database transaction: a Bedrock call can take many
 *       seconds.
 *   <li>The result is stored and {@code ArticleClassified} published together, also when the result
 *       is failed or unclassified, so matching withdraws suggestions for anything that isn't green.
 * </ol>
 *
 * <p>Runs after content-ingestion's transaction commits, or at once if it was published outside
 * one. A database error is logged and doesn't stop ingestion; the version then stays unclassified,
 * which is treated like red (D-4). Logs carry the article id, version and outcome, never the text.
 */
@Component
class ArticleClassification {

  private static final Logger LOG = LoggerFactory.getLogger(ArticleClassification.class);

  private final ClassificationStore store;
  private final ClassificationResults results;
  private final ArticleClassifier classifier;
  private final Clock clock = Clock.systemUTC();

  ArticleClassification(
      ClassificationStore store, ClassificationResults results, ArticleClassifier classifier) {
    this.store = store;
    this.results = results;
    this.classifier = classifier;
  }

  @TransactionalEventListener(fallbackExecution = true)
  void onArticleStored(ArticleStored article) {
    try (MDC.MDCCloseable correlation =
        MDC.putCloseable("correlation_id", article.correlationId())) {
      if (!store.storeUnclassified(
          article.articleId(), article.version(), article.correlationId())) {
        LOG.info(
            "Article {} version {} is stored already; ignored",
            article.articleId(),
            article.version());
        return;
      }
      Classification classification = classify(article);
      results.record(article, classification);
      LOG.info(
          "Article {} version {}: {}{}",
          article.articleId(),
          article.version(),
          classification.status().key(),
          classification.zone() == null ? "" : " " + classification.zone().key());
    } catch (DataAccessException databaseError) {
      LOG.error(
          "Article {} version {} not classified for now; it is treated like red",
          article.articleId(),
          article.version(),
          databaseError);
    }
  }

  private Classification classify(ArticleStored article) {
    ArticleToClassify toClassify;
    try {
      toClassify =
          new ArticleToClassify(
              article.articleId(), article.version(), article.headline(), article.text());
    } catch (IllegalArgumentException incomplete) {
      // No headline or text: nothing to classify, and never green by default.
      LOG.warn(
          "Article {} version {} has no headline or text; stored as failed",
          article.articleId(),
          article.version());
      return Classification.failed(null, clock.instant());
    }
    return classifier.classify(toClassify);
  }
}
