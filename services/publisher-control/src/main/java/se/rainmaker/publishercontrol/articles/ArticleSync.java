package se.rainmaker.publishercontrol.articles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import se.rainmaker.publishercontrol.articles.ArticleChanges.ChangedArticle;

/**
 * One poll of G2's articles API (#150, contract 7.1): stores every changed article and its
 * suggestions, then moves the start of the next poll to the answer's {@code nextSince}.
 *
 * <ul>
 *   <li>G2 down or an unusable answer: logged, nothing stored; the next poll asks again.
 *   <li>A database failure stops the poll before {@code nextSince} moves, so the next poll asks for
 *       the same changes again. Storing them twice changes nothing.
 * </ul>
 */
@Component
public class ArticleSync {

  private static final Logger LOG = LoggerFactory.getLogger(ArticleSync.class);

  private final G2ArticlesClient g2;
  private final ArticleStore store;

  ArticleSync(G2ArticlesClient g2, ArticleStore store) {
    this.g2 = g2;
    this.store = store;
  }

  /** Reads G2's changes once. Never throws for a G2 that is down or sends something unusable. */
  public void pollOnce() {
    ArticleChanges changes;
    try {
      changes = g2.changedSince(store.since());
    } catch (ArticlesUnavailableException unavailable) {
      LOG.warn("Could not read G2's articles: {}", unavailable.getMessage());
      return;
    }
    for (ChangedArticle article : changes.articles()) {
      try (MDC.MDCCloseable correlation =
          MDC.putCloseable("correlation_id", article.correlationId())) {
        store.apply(article);
        LOG.debug("Stored article {} version {}", article.articleId(), article.version());
      }
    }
    store.moveSinceTo(changes.nextSince());
  }
}
