package se.rainmaker.g2contentapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.database.ContentIngestionDatabaseConfiguration;
import se.rainmaker.contentintent.api.ArticleClassified;
import se.rainmaker.contentintent.api.ArticleClassifier;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import se.rainmaker.contentintent.database.ContentIntentDatabaseConfiguration;

/**
 * How content-intent classifies failed and unclassified versions again (#223), with the article's
 * text from content-ingestion. The classifier is a mock; both databases are real (Testcontainers).
 * The schedule is off: each test runs the retry itself. A run takes at most 2 versions here.
 */
@SpringBootTest(
    properties = {
      "rainmaker.content-ingestion.feed.polling=false",
      "rainmaker.content-intent.retry.enabled=false",
      "rainmaker.content-intent.retry.batch-size=2"
    })
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class ClassificationRetryTest {

  private static final Instant AT = Instant.parse("2026-10-30T09:15:04Z");

  /** Long before the retry interval: due for a retry. */
  private static final Instant LONG_AGO = Instant.parse("2026-01-01T00:00:00Z");

  private static final String MODEL = "eu.anthropic.claude-haiku-5-5";

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    // A green article makes matching ask G3, which isn't running: fail fast instead of retrying.
    registry.add("rainmaker.matching-ranking.offers.base-url", () -> "http://localhost:1");
    registry.add("rainmaker.matching-ranking.offers.max-attempts", () -> "1");
  }

  @MockitoBean private ArticleClassifier classifier;
  @Autowired private ApplicationEvents events;

  @Autowired
  @Qualifier("classificationRetry")
  private Runnable retry;

  @Autowired
  @Qualifier(ContentIngestionDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient contentIngestion;

  @Autowired
  @Qualifier(ContentIntentDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient contentIntent;

  /** Other tests share the database: their versions count as just tried, so no run takes them. */
  @BeforeEach
  void leaveOtherTestsVersionsAlone() {
    contentIntent.sql("UPDATE classification SET attempted_at = now()").update();
  }

  @Test
  void failedVersionIsClassifiedAgainStoredAndAnnounced() {
    ArticleStored article = storedInIngestion(UUID.randomUUID(), 1);
    classification(article, "failed", LONG_AGO);
    when(classifier.classify(any())).thenReturn(green());

    retry.run();

    verify(classifier)
        .classify(
            argThat(
                asked ->
                    asked.articleId().equals(article.articleId())
                        && asked.version() == 1
                        && asked.text().equals(article.text())));
    assertThat(row(article)).containsEntry("status", "classified").containsEntry("zone", "green");
    assertThat(published(article))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.classification()).isEqualTo(green());
              assertThat(event.url()).isEqualTo(article.url());
              assertThat(event.correlationId()).isEqualTo(article.correlationId());
            });
  }

  @Test
  void unclassifiedVersionIsClassifiedAgain() {
    ArticleStored article = storedInIngestion(UUID.randomUUID(), 1);
    classification(article, "unclassified", LONG_AGO);
    when(classifier.classify(any())).thenReturn(green());

    retry.run();

    assertThat(row(article)).containsEntry("status", "classified");
    assertThat(published(article)).hasSize(1);
  }

  /** Also a version being classified right now: it was stored as unclassified moments ago. */
  @Test
  void versionTriedRecentlyIsLeftAlone() {
    ArticleStored article = storedInIngestion(UUID.randomUUID(), 1);
    classification(article, "unclassified", Instant.now());

    retry.run();

    verify(classifier, never()).classify(any());
    assertThat(row(article)).containsEntry("status", "unclassified");
  }

  @Test
  void classifiedVersionIsNotClassifiedAgain() {
    ArticleStored article = storedInIngestion(UUID.randomUUID(), 1);
    classifiedGreen(article, LONG_AGO);

    retry.run();

    verify(classifier, never()).classify(any());
  }

  @Test
  void versionReplacedByNewerOneIsNotClassifiedAgain() {
    UUID articleId = UUID.randomUUID();
    ArticleStored first = storedInIngestion(articleId, 1);
    ArticleStored second = storedInIngestion(articleId, 2);
    classification(first, "failed", LONG_AGO);
    classifiedGreen(second, LONG_AGO);

    retry.run();

    verify(classifier, never()).classify(any());
    assertThat(row(first)).containsEntry("status", "failed");
  }

  /** If content-intent missed version 2's event, the retry classifies the latest version. */
  @Test
  void newerVersionContentIntentNeverSawIsClassified() {
    UUID articleId = UUID.randomUUID();
    ArticleStored first = storedInIngestion(articleId, 1);
    final ArticleStored second = storedInIngestion(articleId, 2);
    classification(first, "failed", LONG_AGO);
    when(classifier.classify(any())).thenReturn(green());

    retry.run();

    verify(classifier).classify(argThat(asked -> asked.version() == 2));
    assertThat(row(second)).containsEntry("status", "classified");
    assertThat(published(second)).hasSize(1);
    assertThat(row(first)).containsEntry("status", "failed");
    assertThat(published(first)).isEmpty();

    retry.run();

    verify(classifier, times(1)).classify(any());
  }

  @Test
  void runTakesAtMostBatchSizeOldestFirst() {
    ArticleStored oldest = storedInIngestion(UUID.randomUUID(), 1);
    ArticleStored older = storedInIngestion(UUID.randomUUID(), 1);
    ArticleStored newest = storedInIngestion(UUID.randomUUID(), 1);
    classification(newest, "failed", LONG_AGO.plusSeconds(2));
    classification(oldest, "failed", LONG_AGO);
    classification(older, "failed", LONG_AGO.plusSeconds(1));
    when(classifier.classify(any())).thenReturn(green());

    retry.run();

    assertThat(row(oldest)).containsEntry("status", "classified");
    assertThat(row(older)).containsEntry("status", "classified");
    assertThat(row(newest)).containsEntry("status", "failed");

    retry.run();

    assertThat(row(newest)).containsEntry("status", "classified");
    verify(classifier, times(3)).classify(any());
  }

  @Test
  void versionThatFailsAgainWaitsForTheNextInterval() {
    ArticleStored article = storedInIngestion(UUID.randomUUID(), 1);
    classification(article, "failed", LONG_AGO);
    when(classifier.classify(any())).thenReturn(Classification.failed(MODEL, AT));

    retry.run();
    retry.run();

    verify(classifier, times(1)).classify(any());
    assertThat(row(article)).containsEntry("status", "failed");
    assertThat(published(article)).hasSize(1);
  }

  @Test
  void logsIdVersionAndOutcomeNeverTheText(CapturedOutput output) {
    ArticleStored article = storedInIngestion(UUID.randomUUID(), 1);
    classification(article, "failed", LONG_AGO);
    when(classifier.classify(any())).thenReturn(green());

    retry.run();

    assertThat(output.getAll())
        .contains(article.articleId() + " version 1: classified green")
        .doesNotContain(article.text());
  }

  private static Classification green() {
    return Classification.classified(
        Zone.GREEN, List.of(Topic.HOME), new Reason("Skäl.", "Reason."), MODEL, AT);
  }

  /** Stores the version as content-ingestion does; the text is only in its database. */
  private ArticleStored storedInIngestion(UUID articleId, int version) {
    ArticleStored article =
        new ArticleStored(
            articleId,
            version,
            "http://localhost:3000/artikel/" + articleId,
            "Rubrik " + version,
            "Hemlig brödtext " + articleId + " " + version + ".",
            "bostad",
            "sv",
            AT,
            version == 1 ? null : AT.plusSeconds(3600),
            "correlation-" + articleId + "-" + version);
    contentIngestion
        .sql(
            """
            INSERT INTO article_version (article_id, version, url, headline, body_text, section,
                language, published_at, updated_at, last_modified, correlation_id)
            VALUES (:articleId, :version, :url, :headline, :text, :section, :language,
                :publishedAt, :updatedAt, :publishedAt, :correlationId)
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .param("url", article.url())
        .param("headline", article.headline())
        .param("text", article.text())
        .param("section", article.section())
        .param("language", article.language())
        .param("publishedAt", article.publishedAt().atOffset(ZoneOffset.UTC))
        .param(
            "updatedAt",
            article.updatedAt() == null ? null : article.updatedAt().atOffset(ZoneOffset.UTC))
        .param("correlationId", article.correlationId())
        .update();
    return article;
  }

  /** A failed or unclassified row in content-intent, last tried at {@code attemptedAt}. */
  private void classification(ArticleStored article, String status, Instant attemptedAt) {
    contentIntent
        .sql(
            """
            INSERT INTO classification (article_id, article_version, status, correlation_id,
                attempted_at)
            VALUES (:articleId, :version, :status, :correlationId, :attemptedAt)
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .param("status", status)
        .param("correlationId", article.correlationId())
        .param("attemptedAt", attemptedAt.atOffset(ZoneOffset.UTC))
        .update();
  }

  private void classifiedGreen(ArticleStored article, Instant attemptedAt) {
    contentIntent
        .sql(
            """
            INSERT INTO classification (article_id, article_version, status, zone, topics,
                reason_sv, reason_en, model, classified_at, correlation_id, attempted_at)
            VALUES (:articleId, :version, 'classified', 'green', '{home}', 'Skäl.', 'Reason.',
                :model, :attemptedAt, :correlationId, :attemptedAt)
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .param("model", MODEL)
        .param("correlationId", article.correlationId())
        .param("attemptedAt", attemptedAt.atOffset(ZoneOffset.UTC))
        .update();
  }

  private Map<String, Object> row(ArticleStored article) {
    return contentIntent
        .sql(
            """
            SELECT status, zone FROM classification
            WHERE article_id = :articleId AND article_version = :version
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .query()
        .singleRow();
  }

  private List<ArticleClassified> published(ArticleStored article) {
    return events.stream(ArticleClassified.class)
        .filter(event -> event.articleId().equals(article.articleId()))
        .filter(event -> event.articleVersion() == article.version())
        .toList();
  }
}
