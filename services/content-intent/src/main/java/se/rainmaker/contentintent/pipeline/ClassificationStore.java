package se.rainmaker.contentintent.pipeline;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.database.ContentIntentDatabaseConfiguration;

/**
 * The classification of each article version, in content-intent's own database (#209, contract
 * 3.2). One row per article id and version; never the article's text.
 */
@Component
class ClassificationStore {

  private final JdbcClient database;

  ClassificationStore(
      @Qualifier(ContentIntentDatabaseConfiguration.JDBC_CLIENT) JdbcClient database) {
    this.database = database;
  }

  /**
   * Stores the version as unclassified, unless it is stored already.
   *
   * @return false when this version was stored before, so the event is a repeat
   */
  boolean storeUnclassified(UUID articleId, int version, String correlationId) {
    int rows =
        database
            .sql(
                """
                INSERT INTO classification (article_id, article_version, status, correlation_id)
                VALUES (:articleId, :version, 'unclassified', :correlationId)
                ON CONFLICT DO NOTHING
                """)
            .param("articleId", articleId)
            .param("version", version)
            .param("correlationId", correlationId)
            .update();
    return rows == 1;
  }

  /** Makes {@code classification} the version's current result, replacing any earlier one. */
  void storeResult(UUID articleId, int version, Classification classification) {
    Reason reason = classification.reason();
    database
        .sql(
            """
            UPDATE classification
            SET status = :status, zone = :zone, topics = :topics, reason_sv = :reasonSv,
                reason_en = :reasonEn, model = :model, classified_at = :classifiedAt
            WHERE article_id = :articleId AND article_version = :version
            """)
        .param("articleId", articleId)
        .param("version", version)
        .param("status", classification.status().key())
        .param("zone", classification.zone() == null ? null : classification.zone().key())
        .param("topics", classification.topics().stream().map(Topic::key).toArray(String[]::new))
        .param("reasonSv", reason == null ? null : reason.sv())
        .param("reasonEn", reason == null ? null : reason.en())
        .param("model", classification.model())
        .param("classifiedAt", utc(classification.classifiedAt()))
        .update();
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
