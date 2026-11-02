package se.rainmaker.contentingestion.ingestion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.api.ArticleTexts;
import se.rainmaker.contentingestion.database.ContentIngestionDatabaseConfiguration;

/** Reads the latest version of an article with its text from {@code article_version} (#223). */
@Component
class JdbcArticleTexts implements ArticleTexts {

  private final JdbcClient database;

  JdbcArticleTexts(
      @Qualifier(ContentIngestionDatabaseConfiguration.JDBC_CLIENT) JdbcClient database) {
    this.database = database;
  }

  @Override
  public Optional<ArticleStored> latest(UUID articleId) {
    return database
        .sql(
            """
            SELECT article_id, version, url, headline, body_text, section, language,
                published_at, updated_at, correlation_id
            FROM article_version WHERE article_id = :articleId ORDER BY version DESC LIMIT 1
            """)
        .param("articleId", articleId)
        .query(JdbcArticleTexts::article)
        .optional();
  }

  private static ArticleStored article(ResultSet row, int rowNumber) throws SQLException {
    return new ArticleStored(
        row.getObject("article_id", UUID.class),
        row.getInt("version"),
        row.getString("url"),
        row.getString("headline"),
        row.getString("body_text"),
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
