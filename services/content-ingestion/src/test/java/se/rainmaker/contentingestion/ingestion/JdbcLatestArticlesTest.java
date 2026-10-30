package se.rainmaker.contentingestion.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

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
import se.rainmaker.contentingestion.api.StoredArticle;

/**
 * The latest version of each article, by recency or by URL (#202), against a real PostgreSQL with
 * the module's own migrations. Each row gets its own {@code stored_at}, so the order never depends
 * on the clock.
 */
@Testcontainers
class JdbcLatestArticlesTest {

  private static final String BADRUM = "http://localhost:3000/artikel/badrum";
  private static final String ELBIL = "http://localhost:3000/artikel/elbil";
  private static final Instant PUBLISHED = Instant.parse("2026-10-10T05:45:00Z");

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  private final UUID badrum = UUID.randomUUID();
  private final UUID elbil = UUID.randomUUID();
  private JdbcClient database;
  private JdbcLatestArticles latest;

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
    latest = new JdbcLatestArticles(database);
  }

  @Test
  void recentGivesLatestVersionOfEachArticleNewestStoredFirst() {
    store(badrum, 1, BADRUM, "Badrummet", "2026-10-30T09:00:00Z");
    store(elbil, 1, ELBIL, "Elbilen", "2026-10-30T09:01:00Z");
    store(badrum, 2, BADRUM, "Badrummet, uppdaterad", "2026-10-30T09:02:00Z");

    assertThat(latest.recent(10))
        .extracting(StoredArticle::articleId, StoredArticle::version, StoredArticle::headline)
        .containsExactly(tuple(badrum, 2, "Badrummet, uppdaterad"), tuple(elbil, 1, "Elbilen"));
  }

  @Test
  void recentGivesAtMostCountArticles() {
    store(badrum, 1, BADRUM, "Badrummet", "2026-10-30T09:00:00Z");
    store(elbil, 1, ELBIL, "Elbilen", "2026-10-30T09:01:00Z");

    assertThat(latest.recent(1)).extracting(StoredArticle::articleId).containsExactly(elbil);
  }

  @Test
  void byUrlGivesLatestVersionWithAllFields() {
    store(badrum, 1, BADRUM, "Badrummet", "2026-10-30T09:00:00Z");
    store(badrum, 2, BADRUM, "Badrummet, uppdaterad", "2026-10-30T09:02:00Z");

    assertThat(latest.byUrl(BADRUM))
        .contains(
            new StoredArticle(
                badrum,
                "vb-" + badrum,
                2,
                BADRUM,
                "Badrummet, uppdaterad",
                "bostad",
                "sv",
                PUBLISHED,
                Instant.parse("2026-10-30T09:02:00Z"),
                "correlation-2"));
  }

  @Test
  void unknownUrlGivesNothing() {
    store(badrum, 1, BADRUM, "Badrummet", "2026-10-30T09:00:00Z");

    assertThat(latest.byUrl(ELBIL)).isEmpty();
    assertThat(latest.byUrl(BADRUM + "/")).isEmpty();
  }

  /** Version 1 was never updated; later versions were updated when they were stored. */
  private void store(UUID articleId, int version, String url, String headline, String storedAt) {
    Instant stored = Instant.parse(storedAt);
    database
        .sql(
            """
            INSERT INTO article_version (article_id, version, url, external_id, headline,
                body_text, section, language, published_at, updated_at, last_modified,
                correlation_id, stored_at)
            VALUES (:articleId, :version, :url, :externalId, :headline, 'Brödtext.', 'bostad',
                'sv', :publishedAt, :updatedAt, :lastModified, :correlationId, :storedAt)
            """)
        .param("articleId", articleId)
        .param("version", version)
        .param("url", url)
        .param("externalId", "vb-" + articleId)
        .param("headline", headline)
        .param("publishedAt", PUBLISHED.atOffset(ZoneOffset.UTC))
        .param("updatedAt", version == 1 ? null : stored.atOffset(ZoneOffset.UTC))
        .param("lastModified", stored.atOffset(ZoneOffset.UTC))
        .param("correlationId", "correlation-" + version)
        .param("storedAt", stored.atOffset(ZoneOffset.UTC))
        .update();
  }
}
