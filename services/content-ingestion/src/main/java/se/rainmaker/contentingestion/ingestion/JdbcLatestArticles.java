package se.rainmaker.contentingestion.ingestion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.rainmaker.contentingestion.api.LatestArticles;
import se.rainmaker.contentingestion.api.StoredArticle;
import se.rainmaker.contentingestion.database.ContentIngestionDatabaseConfiguration;

/**
 * Reads the latest version of each article from {@code article_version} (#202). Never the body
 * text: it is not selected.
 */
@Component
class JdbcLatestArticles implements LatestArticles {

  private static final String COLUMNS =
      """
      article_id, external_id, version, url, headline, section, language, published_at,
      updated_at, correlation_id
      """;

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
    // The latest version per article first, then the most recently stored of those. The article
    // id breaks a tie in stored_at (UUIDv7, so the newer article wins).
    return database
        .sql(
            """
            SELECT %s FROM (
                SELECT DISTINCT ON (article_id) * FROM article_version
                ORDER BY article_id, version DESC) latest
            ORDER BY stored_at DESC, article_id DESC
            LIMIT :count
            """
                .formatted(COLUMNS))
        .param("count", count)
        .query(JdbcLatestArticles::article)
        .list();
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
