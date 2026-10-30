package se.rainmaker.g2contentapp.articles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import se.rainmaker.contentingestion.api.LatestArticles;
import se.rainmaker.contentingestion.api.StoredArticle;
import se.rainmaker.contentintent.api.ArticleVersion;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.CurrentClassifications;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.g2contentapp.articles.ArticleList.Article;
import se.rainmaker.g2contentapp.articles.ArticleList.ArticleClassification;
import se.rainmaker.g2contentapp.articles.ArticleList.ArticleSuggestion;
import se.rainmaker.matchingranking.api.CurrentSuggestions;
import se.rainmaker.matchingranking.api.Suggestion;

/**
 * Puts each article together from G2's three modules (#202): the latest version from
 * content-ingestion, its classification from content-intent and its suggestions from matching. It
 * lives in the app, as #202 asks: in content-ingestion or content-intent it would need matching,
 * which already depends on them (a cycle), and matching shouldn't own an API about the other
 * modules' data.
 *
 * <p>Each module is read once for the whole list, never once per article.
 */
@Component
class CurrentArticles {

  private final LatestArticles latest;
  private final CurrentClassifications classifications;
  private final CurrentSuggestions suggestions;

  CurrentArticles(
      LatestArticles latest,
      CurrentClassifications classifications,
      CurrentSuggestions suggestions) {
    this.latest = latest;
    this.classifications = classifications;
    this.suggestions = suggestions;
  }

  /** The {@code count} articles whose latest version was stored most recently, newest first. */
  ArticleList recent(int count) {
    return combine(latest.recent(count));
  }

  /** The article with this exact URL, or an empty list. */
  ArticleList byUrl(String url) {
    return combine(latest.byUrl(url).stream().toList());
  }

  private ArticleList combine(List<StoredArticle> stored) {
    Map<ArticleVersion, Classification> classified =
        classifications.forVersions(stored.stream().map(CurrentArticles::version).toList());
    Map<UUID, List<Suggestion>> sets =
        suggestions.forArticles(stored.stream().map(StoredArticle::articleId).toList());
    return new ArticleList(
        stored.stream()
            .map(
                article ->
                    article(
                        article, classified.get(version(article)), sets.get(article.articleId())))
            .toList());
  }

  private static Article article(
      StoredArticle article, Classification classification, List<Suggestion> set) {
    return new Article(
        article.articleId(),
        article.externalId(),
        article.version(),
        article.url(),
        article.headline(),
        article.section(),
        article.language(),
        article.publishedAt(),
        article.updatedAt(),
        classification(classification),
        suggestions(article, classification, set),
        article.correlationId());
  }

  /**
   * Only a classified green version has suggestions (D-4), and only its own (D-5). The modules are
   * read one after the other, so matching can still hold the set of an older version or of the zone
   * before a reclassification; then the answer has none, never the old set (fail closed).
   */
  private static List<ArticleSuggestion> suggestions(
      StoredArticle article, Classification classification, List<Suggestion> set) {
    if (!classification.mayGetSuggestions()) {
      return List.of();
    }
    return set.stream()
        .filter(suggestion -> suggestion.articleVersion() == article.version())
        .map(
            suggestion ->
                new ArticleSuggestion(
                    suggestion.suggestionId(),
                    suggestion.offerId(),
                    suggestion.rank(),
                    suggestion.matchedTopics()))
        .toList();
  }

  private static ArticleClassification classification(Classification classification) {
    return new ArticleClassification(
        classification.status().key(),
        classification.zone() == null ? null : classification.zone().key(),
        classification.topics().stream().map(Topic::key).toList(),
        classification.reason(),
        classification.model(),
        classification.classifiedAt());
  }

  private static ArticleVersion version(StoredArticle article) {
    return new ArticleVersion(article.articleId(), article.version());
  }
}
