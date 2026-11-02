package se.rainmaker.contentingestion.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The latest stored version of articles with their body text, so content-intent can classify an
 * article again (#223). The text stays inside G2 (contract 3.1): never put it in a REST answer or a
 * log line.
 */
public interface LatestArticleTexts {

  /**
   * The latest version of each article asked for, as {@link ArticleStored} announced it. Read in
   * one query.
   *
   * @return in no particular order; an article that was never stored is left out
   */
  List<ArticleStored> latestOf(Collection<UUID> articleIds);
}
