package se.rainmaker.publishercontrol.editor;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;

/**
 * What the editor page shows (#150, contract 7.6): the most recently published or updated articles
 * (the publisher's own times), each with its open suggestions. Only a classified, green article's
 * latest version has any (D-4, D-5); withdrawn suggestions are never shown.
 */
@Component
class EditorArticles {

  /** How many articles the page shows, the most recently published or updated first. */
  static final int RECENT = 100;

  private final JdbcClient database;

  EditorArticles(
      @Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT) JdbcClient database) {
    this.database = database;
  }

  /** The recent articles with their open suggestions, by rank. */
  List<EditorArticle> recent() {
    Map<UUID, EditorArticle> articles = new LinkedHashMap<>();
    database
        .sql(
            """
            SELECT a.article_id, a.url, a.headline, a.status, a.zone, a.reason_sv, a.reason_en,
                   s.suggestion_id, s.offer_id, s.rank, s.matched_topics,
                   s.status AS suggestion_status
            FROM (SELECT *, COALESCE(updated_at, published_at) AS latest FROM article
                  ORDER BY latest DESC, article_id DESC LIMIT :recent) a
            LEFT JOIN suggestion s
                   ON s.article_id = a.article_id
                  AND s.article_version = a.version
                  AND s.status <> 'withdrawn'
                  AND a.status = 'classified' AND a.zone = 'green'
            ORDER BY a.latest DESC, a.article_id DESC, s.rank
            """)
        .param("recent", RECENT)
        .query(
            (ResultSet row) -> {
              UUID articleId = row.getObject("article_id", UUID.class);
              EditorArticle article = articles.computeIfAbsent(articleId, id -> article(id, row));
              UUID suggestionId = row.getObject("suggestion_id", UUID.class);
              if (suggestionId != null) {
                article.suggestions().add(suggestion(suggestionId, row));
              }
            });
    return List.copyOf(articles.values());
  }

  private static EditorArticle article(UUID articleId, ResultSet row) {
    try {
      return new EditorArticle(
          articleId,
          row.getString("url"),
          row.getString("headline"),
          row.getString("status"),
          row.getString("zone"),
          row.getString("reason_sv"),
          row.getString("reason_en"),
          new ArrayList<>());
    } catch (SQLException exception) {
      throw new IllegalStateException("Cannot read article " + articleId, exception);
    }
  }

  private static EditorSuggestion suggestion(UUID suggestionId, ResultSet row) throws SQLException {
    Array topics = row.getArray("matched_topics");
    return new EditorSuggestion(
        suggestionId,
        row.getObject("offer_id", UUID.class),
        row.getInt("rank"),
        List.of((String[]) topics.getArray()),
        row.getString("suggestion_status"));
  }

  /** One article on the page; {@code zone} is {@code null} unless the status is classified. */
  record EditorArticle(
      UUID articleId,
      String url,
      String headline,
      String status,
      String zone,
      String reasonSv,
      String reasonEn,
      List<EditorSuggestion> suggestions) {}

  /** One open suggestion: {@code status} is suggested, approved or rejected. */
  record EditorSuggestion(
      UUID suggestionId, UUID offerId, int rank, List<String> matchedTopics, String status) {}
}
