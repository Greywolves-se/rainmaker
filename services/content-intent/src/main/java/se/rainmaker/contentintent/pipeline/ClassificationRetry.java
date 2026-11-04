package se.rainmaker.contentintent.pipeline;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.api.LatestArticleTexts;
import se.rainmaker.contentintent.api.ArticleVersion;

/**
 * Classifies failed and unclassified versions again on a schedule (#223). A version is classified
 * once, when it is stored; without this, one that failed (call limits, Bedrock down, an expired
 * login, a database error) stays failed until the article is edited.
 *
 * <p>Each run takes at most {@code batch-size} versions not tried for {@code interval}, oldest
 * attempt first; a version that keeps failing waits twice as long after each failed try, at most
 * {@code max-wait} (#258). It gets their text from content-ingestion and classifies them as the
 * first time, through the same classifier and its call limits and token cap. If content-ingestion
 * has a newer version that content-intent never got, that one is classified instead.
 */
class ClassificationRetry implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ClassificationRetry.class);

  private final ClassificationStore store;
  private final LatestArticleTexts articles;
  private final ArticleClassification classification;
  private final RetrySettings settings;

  ClassificationRetry(
      ClassificationStore store,
      LatestArticleTexts articles,
      ArticleClassification classification,
      RetrySettings settings) {
    this.store = store;
    this.articles = articles;
    this.classification = classification;
    this.settings = settings;
  }

  /** One run. Any error is logged; the versions it took are tried again after the interval. */
  @Override
  public void run() {
    try {
      List<ArticleVersion> due =
          store.takeForRetry(settings.batchSize(), settings.interval(), settings.maxWait());
      if (due.isEmpty()) {
        return;
      }
      LOG.info("Classifying {} failed or unclassified versions again", due.size());
      Map<UUID, ArticleStored> latest =
          articles.latestOf(due.stream().map(ArticleVersion::articleId).toList()).stream()
              .collect(Collectors.toMap(ArticleStored::articleId, Function.identity()));
      due.forEach(version -> retry(version, latest.get(version.articleId())));
    } catch (RuntimeException error) {
      LOG.error("Classifying versions again failed; the next run tries again", error);
    }
  }

  private void retry(ArticleVersion version, ArticleStored latest) {
    if (latest == null || latest.version() < version.version()) {
      LOG.warn(
          "Article {} version {} is not in content-ingestion; not classified again",
          version.articleId(),
          version.version());
    } else if (latest.version() > version.version()) {
      // content-intent never got the newer version's ArticleStored: handle it as that event.
      classification.onArticleStored(latest);
    } else {
      classification.classifyAgain(latest);
    }
  }
}
