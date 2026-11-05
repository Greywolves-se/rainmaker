package se.rainmaker.contentingestion.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import se.rainmaker.contentingestion.api.ArticleStored;
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
  void recentRefusesCountBelowOne() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> latest.recent(0))
        .withMessage("count must be at least 1: 0");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> latest.recent(-1))
        .withMessage("count must be at least 1: -1");
  }

  /**
   * #232: G3 polls the 100 most recent articles every few seconds, so the work must depend on the
   * count asked for, not on how many articles are stored. 10 000 articles, every tenth edited once;
   * the plan of the real query must read about {@code count} rows, never the whole table.
   */
  @Test
  void recentReadsRowsByCountNotByNumberOfArticles() {
    storeMany(10_000);
    database.sql("ANALYZE article_version").update();

    String plan =
        String.join(
            "\n",
            database
                .sql(
                    "EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF) "
                        + JdbcLatestArticles.RECENT.replace(":count", "100"))
                .query(String.class)
                .list());

    assertThat(plan).doesNotContain("Seq Scan on article_version");
    Matcher read =
        Pattern.compile("Index Scan using article_version_recent on article_version .*rows=(\\d+)")
            .matcher(plan);
    assertThat(read.find()).as(plan).isTrue();
    // The 100 latest versions, plus the replaced versions passed on the way.
    assertThat(Integer.parseInt(read.group(1))).as(plan).isLessThanOrEqualTo(200);
  }

  @Test
  void recentGivesTheSameAnswerAmongManyArticles() {
    storeMany(1_000);

    // Every tenth article was edited after all the first versions were stored, in the same order,
    // so the newest are the second versions of articles 1000, 990, 980 ...
    assertThat(latest.recent(3))
        .extracting(StoredArticle::headline, StoredArticle::version)
        .containsExactly(
            tuple("Rubrik 1000, uppdaterad", 2),
            tuple("Rubrik 990, uppdaterad", 2),
            tuple("Rubrik 980, uppdaterad", 2));
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

  @Test
  void latestOfGivesLatestVersionOfEachArticleWithItsText() {
    store(badrum, 1, BADRUM, "Badrummet", "2026-10-30T09:00:00Z");
    store(badrum, 2, BADRUM, "Badrummet, uppdaterad", "2026-10-30T09:02:00Z");
    store(elbil, 1, ELBIL, "Elbilen", "2026-10-30T09:01:00Z");

    assertThat(latest.latestOf(List.of(badrum, elbil, UUID.randomUUID())))
        .containsExactlyInAnyOrder(
            new ArticleStored(
                badrum,
                2,
                BADRUM,
                "Badrummet, uppdaterad",
                "Brödtext.",
                "bostad",
                "sv",
                PUBLISHED,
                Instant.parse("2026-10-30T09:02:00Z"),
                "correlation-2"),
            new ArticleStored(
                elbil,
                1,
                ELBIL,
                "Elbilen",
                "Brödtext.",
                "bostad",
                "sv",
                PUBLISHED,
                null,
                "correlation-1"));
  }

  @Test
  void latestOfNothingGivesNothing() {
    store(badrum, 1, BADRUM, "Badrummet", "2026-10-30T09:00:00Z");

    assertThat(latest.latestOf(List.of())).isEmpty();
  }

  /**
   * Stores {@code count} articles, one second apart, then a second version of every tenth one,
   * stored a day later, so after all of the first versions and in the same order.
   */
  private void storeMany(int count) {
    database
        .sql(
            """
            INSERT INTO article_version (article_id, version, url, external_id, headline,
                body_text, section, language, published_at, last_modified, correlation_id,
                stored_at)
            SELECT gen_random_uuid(), 1, 'http://localhost:3000/artikel/' || n, 'vb-' || n,
                'Rubrik ' || n, 'Brödtext.', 'bostad', 'sv', :publishedAt, :publishedAt,
                'correlation-' || n, TIMESTAMPTZ '2026-10-01 00:00:00Z' + n * INTERVAL '1 second'
            FROM generate_series(1, :count) AS n
            """)
        .param("publishedAt", PUBLISHED.atOffset(ZoneOffset.UTC))
        .param("count", count)
        .update();
    database
        .sql(
            """
            INSERT INTO article_version (article_id, version, url, external_id, headline,
                body_text, section, language, published_at, updated_at, last_modified,
                correlation_id, stored_at)
            SELECT article_id, 2, url, external_id, headline || ', uppdaterad', body_text,
                section, language, published_at, stored_at + INTERVAL '1 day',
                last_modified + INTERVAL '1 day', correlation_id || '-2',
                stored_at + INTERVAL '1 day'
            FROM article_version WHERE version = 1 AND external_id LIKE '%0'
            """)
        .update();
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
