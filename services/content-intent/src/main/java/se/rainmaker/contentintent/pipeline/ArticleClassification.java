package se.rainmaker.contentintent.pipeline;

import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
 *       seconds. If it throws, the result is failed.
 *   <li>The result is stored and {@code ArticleClassified} published together, also when the result
 *       is failed or unclassified, so matching withdraws suggestions for anything that isn't green.
 * </ol>
 *
 * <p>Runs after content-ingestion's transaction commits, or at once if it was published outside
 * one, inside ingestion's feed poll. Any other error, such as a database error, is logged and
 * doesn't stop ingestion; the version then stays unclassified, which is treated like red (D-4).
 * Logs carry the article id, version and outcome, never the text.
 */
class ArticleClassification {

  private static final Logger LOG = LoggerFactory.getLogger(ArticleClassification.class);

  private final ClassificationStore store;
  private final ClassificationResults results;
  private final ArticleClassifier classifier;
  private final Clock clock;

  ArticleClassification(
      ClassificationStore store,
      ClassificationResults results,
      ArticleClassifier classifier,
      Clock clock) {
    this.store = store;
    this.results = results;
    this.classifier = classifier;
    this.clock = Objects.requireNonNull(clock);
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
    } catch (RuntimeException error) {
      // Runs inside ingestion's feed poll: whatever fails here must not stop the other articles.
      LOG.error(
          "Article {} version {} not classified for now; it is treated like red",
          article.articleId(),
          article.version(),
          error);
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
    try {
      return classifier.classify(toClassify);
    } catch (RuntimeException error) {
      // The classifiers turn known errors into failed; anything else must not leave the article
      // unclassified for good, so it is stored and announced as failed too.
      LOG.error(
          "Classifier threw for article {} version {}; stored as failed",
          article.articleId(),
          article.version(),
          error);
      return Classification.failed(null, clock.instant());
    }
  }
}
