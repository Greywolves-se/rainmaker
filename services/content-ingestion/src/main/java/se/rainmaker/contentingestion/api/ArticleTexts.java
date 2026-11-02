package se.rainmaker.contentingestion.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The latest stored version of an article with its text, for content-intent inside G2 (#223): it
 * classifies an article again when the first try failed, and never stores the text itself. The text
 * stays inside G2: never logged, never in a REST answer.
 */
public interface ArticleTexts {

  /**
   * The latest stored version of this article, as the same record the {@link ArticleStored} event
   * carries, or empty if no version is stored.
   */
  Optional<ArticleStored> latest(UUID articleId);
}
