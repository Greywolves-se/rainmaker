package se.rainmaker.contentingestion.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import se.rainmaker.contentingestion.api.ArticleStored;

/**
 * The latest version of an article with its text, for classifying it again (#223), against a real
 * PostgreSQL with the module's own migrations.
 */
@Testcontainers
class JdbcArticleTextsTest {

  private static final String URL = "http://localhost:3000/artikel/badrum";
  private static final Instant PUBLISHED = Instant.parse("2026-10-10T05:45:00Z");

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  private final UUID articleId = UUID.randomUUID();
  private JdbcClient database;
  private JdbcArticleTexts texts;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/content-ingestion")
            .cleanDisabled(false)
            .load();
    flyway.clean();
    flyway.migrate();
    database = JdbcClient.create(dataSource);
    texts = new JdbcArticleTexts(database);
  }

  @Test
  void givesLatestVersionWithHeadlineAndText() {
    store(1, "Badrummet", "Första texten.", "2026-10-30T09:00:00Z");
    store(2, "Badrummet, uppdaterad", "Andra texten.", "2026-10-30T09:02:00Z");

    assertThat(texts.latest(articleId))
        .contains(
            new ArticleStored(
                articleId,
                2,
                URL,
                "Badrummet, uppdaterad",
                "Andra texten.",
                "bostad",
                "sv",
                PUBLISHED,
                Instant.parse("2026-10-30T09:02:00Z"),
                "correlation-2"));
  }

  @Test
  void unknownArticleGivesNothing() {
    store(1, "Badrummet", "Första texten.", "2026-10-30T09:00:00Z");

    assertThat(texts.latest(UUID.randomUUID())).isEmpty();
  }

  /** Version 1 was never updated; later versions were updated when they were stored. */
  private void store(int version, String headline, String text, String storedAt) {
    Instant stored = Instant.parse(storedAt);
    database
        .sql(
            """
            INSERT INTO article_version (article_id, version, url, external_id, headline,
                body_text, section, language, published_at, updated_at, last_modified,
                correlation_id, stored_at)
            VALUES (:articleId, :version, :url, :externalId, :headline, :text, 'bostad', 'sv',
                :publishedAt, :updatedAt, :lastModified, :correlationId, :storedAt)
            """)
        .param("articleId", articleId)
        .param("version", version)
        .param("url", URL)
        .param("externalId", "vb-" + articleId)
        .param("headline", headline)
        .param("text", text)
        .param("publishedAt", PUBLISHED.atOffset(ZoneOffset.UTC))
        .param("updatedAt", version == 1 ? null : stored.atOffset(ZoneOffset.UTC))
        .param("lastModified", stored.atOffset(ZoneOffset.UTC))
        .param("correlationId", "correlation-" + version)
        .param("storedAt", stored.atOffset(ZoneOffset.UTC))
        .update();
  }
}
