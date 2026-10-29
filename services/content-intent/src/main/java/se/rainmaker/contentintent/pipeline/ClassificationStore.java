package se.rainmaker.contentintent.pipeline;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.rainmaker.contentintent.api.ArticleVersion;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.ClassificationStatus;
import se.rainmaker.contentintent.api.CurrentClassifications;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import se.rainmaker.contentintent.database.ContentIntentDatabaseConfiguration;

/**
 * The classification of each article version, in content-intent's own database (#209, contract
 * 3.2). One row per article id and version; never the article's text.
 */
@Component
class ClassificationStore implements CurrentClassifications {

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

  /**
   * Makes {@code classification} the version's current result, replacing any earlier one.
   *
   * @throws IllegalStateException if the version wasn't stored as unclassified first
   */
  void storeResult(UUID articleId, int version, Classification classification) {
    Reason reason = classification.reason();
    int rows =
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
            .param(
                "topics", classification.topics().stream().map(Topic::key).toArray(String[]::new))
            .param("reasonSv", reason == null ? null : reason.sv())
            .param("reasonEn", reason == null ? null : reason.en())
            .param("model", classification.model())
            .param("classifiedAt", utc(classification.classifiedAt()))
            .update();
    if (rows != 1) {
      // Rolls back the transaction, so ArticleClassified is never published for a missing row.
      throw new IllegalStateException(
          "Article " + articleId + " version " + version + " has no classification row");
    }
  }

  @Override
  public Map<ArticleVersion, Classification> forVersions(Collection<ArticleVersion> versions) {
    Map<ArticleVersion, Classification> answer = new LinkedHashMap<>();
    versions.forEach(version -> answer.put(version, Classification.unclassified()));
    if (answer.isEmpty()) {
      return answer;
    }
    // One query for all of them: the versions asked for are joined as two parallel arrays.
    database
        .sql(
            """
            SELECT article_id, article_version, status, zone, topics, reason_sv, reason_en, model,
                classified_at
            FROM classification
            JOIN unnest(CAST(:articleIds AS uuid[]), CAST(:versions AS integer[]))
                AS asked (article_id, article_version) USING (article_id, article_version)
            """)
        .param(
            "articleIds",
            answer.keySet().stream().map(key -> key.articleId().toString()).toArray(String[]::new))
        .param(
            "versions",
            answer.keySet().stream()
                .map(key -> String.valueOf(key.version()))
                .toArray(String[]::new))
        .query(
            (row, number) ->
                Map.entry(
                    new ArticleVersion(
                        row.getObject("article_id", UUID.class), row.getInt("article_version")),
                    classification(row)))
        .list()
        .forEach(stored -> answer.put(stored.getKey(), stored.getValue()));
    return answer;
  }

  private static Classification classification(ResultSet row) throws SQLException {
    String status = row.getString("status");
    OffsetDateTime at = row.getObject("classified_at", OffsetDateTime.class);
    Instant classifiedAt = at == null ? null : at.toInstant();
    if (ClassificationStatus.CLASSIFIED.key().equals(status)) {
      return Classification.classified(
          Zone.fromKey(row.getString("zone")).orElseThrow(),
          Arrays.stream((String[]) row.getArray("topics").getArray())
              .map(key -> Topic.fromKey(key).orElseThrow())
              .toList(),
          new Reason(row.getString("reason_sv"), row.getString("reason_en")),
          row.getString("model"),
          classifiedAt);
    }
    if (ClassificationStatus.FAILED.key().equals(status)) {
      return Classification.failed(row.getString("model"), classifiedAt);
    }
    return Classification.unclassified();
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
