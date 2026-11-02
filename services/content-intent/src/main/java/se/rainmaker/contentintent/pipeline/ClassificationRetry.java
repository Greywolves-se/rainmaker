package se.rainmaker.contentintent.pipeline;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.api.ArticleTexts;
import se.rainmaker.contentintent.api.ArticleVersion;
import se.rainmaker.contentintent.api.Classification;

/**
 * Classifies failed and unclassified article versions again, on a schedule (#223). Without it, a
 * version that failed (Bedrock down, the circuit breaker open, an expired login) or was left
 * unclassified (the call limit or token cap used, a database error) stayed that way until the
 * article was edited.
 *
 * <p>Each run picks at most {@code batch-size} of them, tried at least {@code interval} ago, oldest
 * first, and only the newest version of each article. The text comes from content-ingestion, since
 * this module never stores it. The same classifier is used as for the first try, so the call limit,
 * the token cap and the circuit breaker all apply. The result is stored and {@code
 * ArticleClassified} published, as for a first classification. Logs carry the article id, version
 * and outcome, never the text.
 */
class ClassificationRetry {

  private static final Logger LOG = LoggerFactory.getLogger(ClassificationRetry.class);

  private final ClassificationStore store;
  private final ArticleTexts texts;
  private final ArticleClassification classification;
  private final RetryProperties settings;
  private final Clock clock;

  ClassificationRetry(
      ClassificationStore store,
      ArticleTexts texts,
      ArticleClassification classification,
      RetryProperties settings,
      Clock clock) {
    this.store = store;
    this.texts = texts;
    this.classification = classification;
    this.settings = settings;
    this.clock = Objects.requireNonNull(clock);
  }

  /** One run: picks the versions due and tries each of them once. */
  void retryOnce() {
    Instant now = clock.instant();
    List<ArticleVersion> due;
    try {
      due = store.pickForRetry(now.minus(settings.interval()), now, settings.batchSize());
    } catch (RuntimeException error) {
      LOG.error("Could not read the classifications to retry; trying again next run", error);
      return;
    }
    if (!due.isEmpty()) {
      LOG.info("Retrying the classification of {} article versions", due.size());
    }
    due.forEach(this::retry);
  }

  private void retry(ArticleVersion version) {
    try {
      Optional<ArticleStored> latest = texts.latest(version.articleId());
      if (latest.isEmpty() || latest.get().version() != version.version()) {
        // A newer version is classified on its own when it is stored; this one is out of date.
        LOG.info(
            "Article {} version {} not retried: ingestion has a newer version, or none",
            version.articleId(),
            version.version());
        return;
      }
      ArticleStored article = latest.get();
      try (MDC.MDCCloseable correlation =
          MDC.putCloseable("correlation_id", article.correlationId())) {
        Classification result = classification.classifyAndRecord(article);
        LOG.info(
            "Article {} version {} retried: {}",
            article.articleId(),
            article.version(),
            ArticleClassification.outcome(result));
      }
    } catch (RuntimeException error) {
      // One version's error must not stop the others; it is tried again after the next interval.
      LOG.error(
          "Article {} version {} not retried for now; it is treated like red",
          version.articleId(),
          version.version(),
          error);
    }
  }
}
