package se.rainmaker.contentingestion.ingestion;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import se.rainmaker.contentingestion.api.ArticleStored;

/**
 * The stored versions of every article (table {@code article_version}, V2). Append-only: a new
 * version is a new row, and rows are never updated or deleted.
 */
class ArticleVersions {

  /** The newest stored version of one URL. */
  record LatestVersion(UUID articleId, int version, Instant lastModified) {}

  private final JdbcClient database;

  ArticleVersions(JdbcClient database) {
    this.database = database;
  }

  /** The newest version stored for {@code url}, or empty for an article never seen before. */
  Optional<LatestVersion> latest(String url) {
    return database
        .sql(
            """
            SELECT article_id, version, last_modified FROM article_version
            WHERE url = :url ORDER BY version DESC LIMIT 1
            """)
        .param("url", url)
        .query(
            (row, number) ->
                new LatestVersion(
                    row.getObject("article_id", UUID.class),
                    row.getInt("version"),
                    row.getObject("last_modified", OffsetDateTime.class).toInstant()))
        .optional();
  }

  /**
   * Stores one version.
   *
   * @return {@code false} when this URL already has this version, so it is never stored twice
   */
  boolean insert(ArticleStored article, String externalId, Instant lastModified) {
    int rows =
        database
            .sql(
                """
                INSERT INTO article_version (article_id, version, url, external_id, headline,
                    body_text, section, language, published_at, updated_at, last_modified,
                    correlation_id)
                VALUES (:articleId, :version, :url, :externalId, :headline, :text, :section,
                    :language, :publishedAt, :updatedAt, :lastModified, :correlationId)
                ON CONFLICT DO NOTHING
                """)
            .param("articleId", article.articleId())
            .param("version", article.version())
            .param("url", article.url())
            .param("externalId", externalId)
            .param("headline", article.headline())
            .param("text", article.text())
            .param("section", article.section())
            .param("language", article.language())
            .param("publishedAt", utc(article.publishedAt()))
            .param("updatedAt", utc(article.updatedAt()))
            .param("lastModified", utc(lastModified))
            .param("correlationId", article.correlationId())
            .update();
    return rows == 1;
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
