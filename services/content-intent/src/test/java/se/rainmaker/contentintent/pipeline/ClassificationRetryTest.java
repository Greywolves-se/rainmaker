package se.rainmaker.contentintent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.api.ArticleTexts;
import se.rainmaker.contentintent.api.ArticleClassified;
import se.rainmaker.contentintent.api.ArticleClassifier;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.ClassificationStatus;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;

/**
 * Failed and unclassified article versions are classified again on a schedule (#223). The database
 * is a real PostgreSQL with content-intent's migrations; the classifier and ingestion's texts are
 * mocks, and each step runs at a time the test picks.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ClassificationRetryTest {

  private static final Instant START = Instant.parse("2026-11-02T09:00:00Z");
  private static final Duration INTERVAL = Duration.ofMinutes(5);
  private static final String MODEL = "eu.anthropic.claude-haiku-5-5";
  private static final String SECRET_TEXT = "Brödtext som aldrig får loggas.";

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  private final ArticleClassifier classifier = mock(ArticleClassifier.class);
  private final ArticleTexts texts = mock(ArticleTexts.class);
  private final List<Object> published = new ArrayList<>();
  private JdbcClient database;
  private ClassificationStore store;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/content-intent")
            .cleanDisabled(false)
            .load();
    flyway.clean();
    flyway.migrate();
    database = JdbcClient.create(dataSource);
    store = new ClassificationStore(database);
  }

  @Test
  void failedVersionIsClassifiedAgainStoredAndPublished() {
    ArticleStored article = ingestionHas(stored(UUID.randomUUID(), 1));
    when(classifier.classify(any())).thenReturn(Classification.failed(MODEL, START));
    classification(START).onArticleStored(article);
    when(classifier.classify(any())).thenReturn(green());

    retry(START.plus(INTERVAL), 10).retryOnce();

    assertThat(status(article)).isEqualTo("classified");
    assertThat(classified(article))
        .extracting(event -> event.classification().status(), ArticleClassified::correlationId)
        .containsExactly(
            tuple(ClassificationStatus.FAILED, article.correlationId()),
            tuple(ClassificationStatus.CLASSIFIED, article.correlationId()));
  }

  @Test
  void unclassifiedVersionIsClassifiedAgain() {
    ArticleStored article = ingestionHas(stored(UUID.randomUUID(), 1));
    // Left unclassified, e.g. the result couldn't be stored, or the call limit was used.
    store.storeUnclassified(article.articleId(), 1, article.correlationId(), START);
    when(classifier.classify(any())).thenReturn(green());

    retry(START.plus(INTERVAL), 10).retryOnce();

    assertThat(status(article)).isEqualTo("classified");
    assertThat(classified(article)).hasSize(1);
  }

  @Test
  void versionTriedLessThanAnIntervalAgoIsNotRetried() {
    ArticleStored article = ingestionHas(stored(UUID.randomUUID(), 1));
    when(classifier.classify(any())).thenReturn(Classification.failed(MODEL, START));
    classification(START).onArticleStored(article);

    retry(START.plus(INTERVAL).minusSeconds(1), 10).retryOnce();

    verify(classifier, times(1)).classify(any());
    assertThat(status(article)).isEqualTo("failed");
  }

  @Test
  void classifiedVersionIsNeverRetried() {
    ArticleStored article = ingestionHas(stored(UUID.randomUUID(), 1));
    when(classifier.classify(any())).thenReturn(green());
    classification(START).onArticleStored(article);

    retry(START.plus(Duration.ofHours(1)), 10).retryOnce();

    verify(classifier, times(1)).classify(any());
  }

  @Test
  void versionReplacedByNewerClassifiedOneIsNotRetried() {
    UUID articleId = UUID.randomUUID();
    when(classifier.classify(any()))
        .thenReturn(Classification.failed(MODEL, START))
        .thenReturn(green());
    classification(START).onArticleStored(stored(articleId, 1));
    classification(START).onArticleStored(stored(articleId, 2));

    retry(START.plus(INTERVAL), 10).retryOnce();

    verify(classifier, times(2)).classify(any());
    verify(texts, never()).latest(any());
    assertThat(status(stored(articleId, 1))).isEqualTo("failed");
  }

  /** Ingestion already has a newer version that content-intent hasn't seen yet. */
  @Test
  void versionWhoseArticleHasNewerTextIsNotRetried() {
    UUID articleId = UUID.randomUUID();
    when(classifier.classify(any())).thenReturn(Classification.failed(MODEL, START));
    classification(START).onArticleStored(stored(articleId, 1));
    ingestionHas(stored(articleId, 2));

    retry(START.plus(INTERVAL), 10).retryOnce();

    verify(classifier, times(1)).classify(any());
    assertThat(status(stored(articleId, 1))).isEqualTo("failed");
    assertThat(published).hasSize(1);
  }

  @Test
  void retriesAtMostBatchSizePerRunOldestFirst() {
    List<ArticleStored> articles = new ArrayList<>();
    when(classifier.classify(any())).thenReturn(Classification.failed(MODEL, START));
    for (int minute = 0; minute < 3; minute++) {
      ArticleStored article = ingestionHas(stored(UUID.randomUUID(), 1));
      articles.add(article);
      classification(START.plusSeconds(60L * minute)).onArticleStored(article);
    }
    when(classifier.classify(any())).thenReturn(green());
    Instant later = START.plus(Duration.ofHours(1));

    retry(later, 2).retryOnce();

    assertThat(articles)
        .extracting(this::status)
        .containsExactly("classified", "classified", "failed");

    retry(later.plus(INTERVAL), 2).retryOnce();

    assertThat(articles).extracting(this::status).containsOnly("classified");
  }

  /** A retry that fails again waits another interval, so a lasting error isn't hammered. */
  @Test
  void classifierThatThrowsLeavesItFailedUntilTheNextInterval() {
    ArticleStored article = ingestionHas(stored(UUID.randomUUID(), 1));
    when(classifier.classify(any())).thenReturn(Classification.failed(MODEL, START));
    classification(START).onArticleStored(article);
    when(classifier.classify(any())).thenThrow(new IllegalStateException("Unexpected answer"));
    Instant firstRetry = START.plus(INTERVAL);

    retry(firstRetry, 10).retryOnce();
    retry(firstRetry.plus(INTERVAL).minusSeconds(1), 10).retryOnce();

    verify(classifier, times(2)).classify(any());
    assertThat(status(article)).isEqualTo("failed");
    assertThat(classified(article)).hasSize(2);
  }

  @Test
  void logsArticleVersionAndOutcomeNeverTheText(CapturedOutput output) {
    ArticleStored article = ingestionHas(stored(UUID.randomUUID(), 1));
    when(classifier.classify(any())).thenReturn(Classification.failed(MODEL, START));
    classification(START).onArticleStored(article);
    when(classifier.classify(any())).thenReturn(green());

    retry(START.plus(INTERVAL), 10).retryOnce();

    assertThat(output)
        .contains("Article " + article.articleId() + " version 1 retried: classified green")
        .doesNotContain(SECRET_TEXT);
  }

  private ArticleClassification classification(Instant now) {
    return new ArticleClassification(
        store, new ClassificationResults(store, published::add), classifier, clock(now));
  }

  private ClassificationRetry retry(Instant now, int batchSize) {
    return new ClassificationRetry(
        store, texts, classification(now), new RetryProperties(INTERVAL, batchSize), clock(now));
  }

  /** Ingestion's latest version of the article, with its text. */
  private ArticleStored ingestionHas(ArticleStored article) {
    doReturn(Optional.of(article)).when(texts).latest(article.articleId());
    return article;
  }

  private static ArticleStored stored(UUID articleId, int version) {
    ArticleStored article =
        new ArticleStored(
            articleId,
            version,
            "http://localhost:3000/artikel/" + articleId,
            "Rubrik " + version,
            SECRET_TEXT,
            "bostad",
            "sv",
            START,
            null,
            "correlation-" + articleId + "-" + version);
    return article;
  }

  private static Classification green() {
    return Classification.classified(
        Zone.GREEN, List.of(Topic.HOME), new Reason("Skäl.", "Reason."), MODEL, START);
  }

  private List<ArticleClassified> classified(ArticleStored article) {
    return published.stream()
        .map(ArticleClassified.class::cast)
        .filter(event -> event.articleId().equals(article.articleId()))
        .filter(event -> event.articleVersion() == article.version())
        .toList();
  }

  private String status(ArticleStored article) {
    return database
        .sql(
            """
            SELECT status FROM classification
            WHERE article_id = :articleId AND article_version = :version
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .query(String.class)
        .single();
  }

  private static Clock clock(Instant now) {
    return Clock.fixed(now, ZoneOffset.UTC);
  }
}
