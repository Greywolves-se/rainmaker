package se.rainmaker.contentingestion.ingestion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.api.LatestArticleTexts;
import se.rainmaker.contentingestion.api.LatestArticles;
import se.rainmaker.contentingestion.api.StoredArticle;
import se.rainmaker.contentingestion.database.ContentIngestionDatabaseConfiguration;

/**
 * Reads the latest version of each article from {@code article_version}: without the body text for
 * the articles API (#202), with it only for classifying again inside G2 (#223).
 */
@Component
class JdbcLatestArticles implements LatestArticles, LatestArticleTexts {

  private static final String COLUMNS =
      """
      article_id, external_id, version, url, headline, section, language, published_at,
      updated_at, correlation_id
      """;

  /**
   * The latest version of each article, the most recently stored first. The article id breaks a tie
   * in stored_at (UUIDv7, so the newer article wins). It walks the index {@code
   * article_version_recent} newest first, skips a version that a newer one of the same article has
   * replaced (the primary key answers that), and stops after {@code count}: the rows read grow with
   * the count, not with the number of articles (#232). Package-private for the test of its plan.
   */
  static final String RECENT =
      """
      SELECT %s FROM article_version current
      WHERE NOT EXISTS (
          SELECT 1 FROM article_version newer
          WHERE newer.article_id = current.article_id AND newer.version > current.version)
      ORDER BY stored_at DESC, article_id DESC
      LIMIT :count
      """
          .formatted(COLUMNS);

  private final JdbcClient database;

  JdbcLatestArticles(
      @Qualifier(ContentIngestionDatabaseConfiguration.JDBC_CLIENT) JdbcClient database) {
    this.database = database;
  }

  @Override
  public List<StoredArticle> recent(int count) {
    if (count < 1) {
      throw new IllegalArgumentException("count must be at least 1: " + count);
    }
    return database.sql(RECENT).param("count", count).query(JdbcLatestArticles::article).list();
  }

  @Override
  public Optional<StoredArticle> byUrl(String url) {
    return database
        .sql(
            "SELECT %s FROM article_version WHERE url = :url ORDER BY version DESC LIMIT 1"
                .formatted(COLUMNS))
        .param("url", url)
        .query(JdbcLatestArticles::article)
        .optional();
  }

  @Override
  public List<ArticleStored> latestOf(Collection<UUID> articleIds) {
    if (articleIds.isEmpty()) {
      return List.of();
    }
    return database
        .sql(
            """
            SELECT DISTINCT ON (article_id) %s, body_text FROM article_version
            WHERE article_id = ANY (CAST(:articleIds AS uuid[]))
            ORDER BY article_id, version DESC
            """
                .formatted(COLUMNS))
        .param("articleIds", articleIds.stream().map(UUID::toString).toArray(String[]::new))
        .query(JdbcLatestArticles::withText)
        .list();
  }

  private static ArticleStored withText(ResultSet row, int rowNumber) throws SQLException {
    StoredArticle article = article(row, rowNumber);
    return new ArticleStored(
        article.articleId(),
        article.version(),
        article.url(),
        article.headline(),
        row.getString("body_text"),
        article.section(),
        article.language(),
        article.publishedAt(),
        article.updatedAt(),
        article.correlationId());
  }

  private static StoredArticle article(ResultSet row, int rowNumber) throws SQLException {
    return new StoredArticle(
        row.getObject("article_id", UUID.class),
        row.getString("external_id"),
        row.getInt("version"),
        row.getString("url"),
        row.getString("headline"),
        row.getString("section"),
        row.getString("language"),
        instant(row.getObject("published_at", OffsetDateTime.class)),
        instant(row.getObject("updated_at", OffsetDateTime.class)),
        row.getString("correlation_id"));
  }

  private static Instant instant(OffsetDateTime time) {
    return time == null ? null : time.toInstant();
  }
}
