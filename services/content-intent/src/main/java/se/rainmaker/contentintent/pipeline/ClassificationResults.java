package se.rainmaker.contentintent.pipeline;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentintent.api.ArticleClassified;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.database.ContentIntentDatabaseConfiguration;

/**
 * Stores a classifier's result and announces it to matching in one transaction (#209, contract
 * 6.2), so matching, which listens after the commit, never sees a result that wasn't stored.
 */
@Component
class ClassificationResults {

  private final ClassificationStore store;
  private final ApplicationEventPublisher events;

  ClassificationResults(ClassificationStore store, ApplicationEventPublisher events) {
    this.store = store;
    this.events = events;
  }

  /** Stores the result for this version and publishes {@link ArticleClassified}, whatever it is. */
  @Transactional(ContentIntentDatabaseConfiguration.TRANSACTION_MANAGER)
  public void record(ArticleStored article, Classification classification) {
    store.storeResult(article.articleId(), article.version(), classification);
    events.publishEvent(
        new ArticleClassified(
            article.articleId(),
            article.version(),
            article.url(),
            article.headline(),
            classification,
            article.correlationId()));
  }
}
