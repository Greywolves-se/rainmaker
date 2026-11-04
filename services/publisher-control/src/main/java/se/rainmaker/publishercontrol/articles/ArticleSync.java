package se.rainmaker.publishercontrol.articles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import se.rainmaker.publishercontrol.articles.ArticleList.G2Article;

/**
 * One poll of G2's articles API (#150, contract 7.1): asks for a snapshot of the recent articles
 * and stores each one with its suggestions. There is no bookmark: every poll gets the current state
 * of every recent article, so a reclassification or a lost answer is caught by the next poll.
 *
 * <ul>
 *   <li>G2 down or an unusable answer: logged, nothing stored; the next poll asks again.
 *   <li>A database failure stops the poll; the next poll sends the same articles again. Storing an
 *       article twice changes nothing.
 * </ul>
 *
 * <p>After the demo, events replace this poll (ADR 0004): an event listener then hands each article
 * to {@link ArticleStore#apply} the same way.
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

  /**
   * Reads G2's recent articles once. Never throws for a G2 that is down or sends something
   * unusable.
   */
  public void pollOnce() {
    ArticleList recent;
    try {
      recent = g2.recent();
    } catch (ArticlesUnavailableException unavailable) {
      LOG.warn("Could not read G2's articles: {}", unavailable.getMessage());
      return;
    }
    for (G2Article article : recent.articles()) {
      try (MDC.MDCCloseable correlation =
          MDC.putCloseable("correlation_id", article.correlationId())) {
        store.apply(article);
        LOG.debug("Stored article {} version {}", article.articleId(), article.version());
      }
    }
  }
}
