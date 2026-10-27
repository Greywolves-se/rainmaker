package se.rainmaker.publishercontrol.articles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.rainmaker.publishercontrol.articles.ArticleChanges.ChangedArticle;
import se.rainmaker.publishercontrol.articles.ArticleChanges.SuggestedOffer;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;

/**
 * G3's copy of G2's articles and suggestions, with the editor's decisions (#150, contract 5).
 *
 * <ul>
 *   <li>An older article version than the one stored is ignored.
 *   <li>The suggestions G2 sends are the complete current set: every open suggestion that isn't in
 *       it is withdrawn, even an approved one (D-5). A new version brings new suggestion ids, so it
 *       withdraws the whole earlier set.
 *   <li>A suggestion G2 sends again keeps the editor's decision, so the same answer twice changes
 *       nothing.
 *   <li>An article that isn't classified + green keeps no open suggestions, whatever G2 sends
 *       (D-4).
 * </ul>
 *
 * <p>The article's row is locked while it changes, so an editor's decision waits for it.
 */
@Component
class ArticleStore {

  private static final String SOURCE = "g2-articles";

  private final JdbcClient database;

  ArticleStore(@Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT) JdbcClient database) {
    this.database = database;
  }

  /** Stores the article's latest version and its suggestion set. */
  @Transactional(PublisherControlDatabaseConfiguration.TRANSACTION_MANAGER)
  public void apply(ChangedArticle article) {
    insertIfNew(article);
    int stored = lockVersion(article.articleId());
    if (article.version() < stored) {
      return;
    }
    update(article);
    List<SuggestedOffer> current = article.isGreen() ? article.suggestions() : List.of();
    List<UUID> keep = current.stream().map(SuggestedOffer::suggestionId).toList();
    for (UUID open : openSuggestions(article.articleId())) {
      if (!keep.contains(open)) {
        withdraw(open);
      }
    }
    for (SuggestedOffer suggestion : current) {
      insertSuggestionIfNew(article, suggestion);
    }
  }

  /** Where the next poll starts: the {@code nextSince} of the last answer stored. */
  Instant since() {
    return database
        .sql("SELECT since FROM sync_position WHERE source = ?")
        .param(SOURCE)
        .query(Timestamp.class)
        .single()
        .toInstant();
  }

  /** Moves the start of the next poll forward, never back. */
  void moveSinceTo(Instant nextSince) {
    database
        .sql("UPDATE sync_position SET since = GREATEST(since, ?) WHERE source = ?")
        .params(Timestamp.from(nextSince), SOURCE)
        .update();
  }

  private void insertIfNew(ChangedArticle article) {
    database
        .sql(
            """
            INSERT INTO article (article_id, version, url, headline, published_at, status, zone,
                                 reason_sv, reason_en, changed_at)
            VALUES (:articleId, :version, :url, :headline, :publishedAt, :status, :zone,
                    :reasonSv, :reasonEn, :changedAt)
            ON CONFLICT (article_id) DO NOTHING
            """)
        .paramSource(columns(article))
        .update();
  }

  private int lockVersion(UUID articleId) {
    return database
        .sql("SELECT version FROM article WHERE article_id = ? FOR UPDATE")
        .param(articleId)
        .query(Integer.class)
        .single();
  }

  private void update(ChangedArticle article) {
    database
        .sql(
            """
            UPDATE article
            SET version = :version, url = :url, headline = :headline, published_at = :publishedAt,
                status = :status, zone = :zone, reason_sv = :reasonSv, reason_en = :reasonEn,
                changed_at = :changedAt
            WHERE article_id = :articleId
            """)
        .paramSource(columns(article))
        .update();
  }

  private List<UUID> openSuggestions(UUID articleId) {
    return database
        .sql("SELECT suggestion_id FROM suggestion WHERE article_id = ? AND status <> 'withdrawn'")
        .param(articleId)
        .query(UUID.class)
        .list();
  }

  private void withdraw(UUID suggestionId) {
    database
        .sql("UPDATE suggestion SET status = 'withdrawn' WHERE suggestion_id = ?")
        .param(suggestionId)
        .update();
  }

  /** A suggestion seen before keeps its status; a withdrawn one is never opened again. */
  private void insertSuggestionIfNew(ChangedArticle article, SuggestedOffer suggestion) {
    database
        .sql(
            """
            INSERT INTO suggestion (suggestion_id, article_id, article_version, offer_id, rank,
                                    matched_topics)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (suggestion_id) DO NOTHING
            """)
        .params(
            suggestion.suggestionId(),
            article.articleId(),
            article.version(),
            suggestion.offerId(),
            suggestion.rank(),
            suggestion.matchedTopics().toArray(String[]::new))
        .update();
  }

  private static ArticleColumns columns(ChangedArticle article) {
    ArticleChanges.Reason reason = article.classification().reason();
    return new ArticleColumns(
        article.articleId(),
        article.version(),
        article.url(),
        article.headline(),
        article.publishedAt() == null ? null : Timestamp.from(article.publishedAt()),
        article.classification().status(),
        article.classification().zone(),
        reason == null ? null : reason.sv(),
        reason == null ? null : reason.en(),
        Timestamp.from(article.changedAt()));
  }

  /** The article's columns, as named parameters. */
  record ArticleColumns(
      UUID articleId,
      int version,
      String url,
      String headline,
      Timestamp publishedAt,
      String status,
      String zone,
      String reasonSv,
      String reasonEn,
      Timestamp changedAt) {}
}
