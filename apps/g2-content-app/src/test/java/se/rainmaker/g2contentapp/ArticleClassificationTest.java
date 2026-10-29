package se.rainmaker.g2contentapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentintent.api.ArticleClassified;
import se.rainmaker.contentintent.api.ArticleClassifier;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.ClassificationStatus;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import se.rainmaker.contentintent.database.ContentIntentDatabaseConfiguration;

/**
 * How content-intent handles a stored article in G2 (#209): stored as unclassified, classified,
 * then the result stored and ArticleClassified published. The classifier is a mock, so each test
 * picks the answer; the database is real (Testcontainers). Feed polling is off.
 */
@SpringBootTest(properties = "rainmaker.content-ingestion.feed.polling=false")
@RecordApplicationEvents
class ArticleClassificationTest {

  private static final Instant AT = Instant.parse("2026-10-30T09:15:04Z");
  private static final Reason REASON = new Reason("Skäl.", "Reason.");
  private static final String MODEL = "eu.anthropic.claude-haiku-5-5";

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    // A green article makes matching ask G3, which isn't running: fail fast instead of retrying.
    registry.add("rainmaker.matching-ranking.offers.base-url", () -> "http://localhost:1");
    registry.add("rainmaker.matching-ranking.offers.max-attempts", () -> "1");
  }

  @MockitoBean private ArticleClassifier classifier;
  @Autowired private ApplicationEventPublisher publisher;
  @Autowired private ApplicationEvents events;

  @Autowired
  @Qualifier(ContentIntentDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient database;

  @Test
  void storesUnclassifiedAtOnceThenTheClassification() {
    ArticleStored article = stored(UUID.randomUUID(), 1);
    AtomicReference<String> statusWhileClassifying = new AtomicReference<>();
    when(classifier.classify(any()))
        .thenAnswer(
            call -> {
              statusWhileClassifying.set(row(article).get("status").toString());
              return green(Topic.HOME, Topic.TRAVEL);
            });

    publisher.publishEvent(article);

    assertThat(statusWhileClassifying).hasValue("unclassified");
    Map<String, Object> row = row(article);
    assertThat(row)
        .containsEntry("status", "classified")
        .containsEntry("zone", "green")
        .containsEntry("reason_sv", "Skäl.")
        .containsEntry("reason_en", "Reason.")
        .containsEntry("model", MODEL)
        .containsEntry("correlation_id", article.correlationId());
    assertThat(topics(article)).containsExactly("home", "travel");
    assertThat(published(article))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.articleVersion()).isEqualTo(1);
              assertThat(event.url()).isEqualTo(article.url());
              assertThat(event.headline()).isEqualTo(article.headline());
              assertThat(event.classification().zone()).isEqualTo(Zone.GREEN);
              assertThat(event.correlationId()).isEqualTo(article.correlationId());
            });
  }

  @Test
  void failedClassificationIsStoredWithoutZoneAndPublished() {
    ArticleStored article = stored(UUID.randomUUID(), 1);
    when(classifier.classify(any())).thenReturn(Classification.failed(MODEL, AT));

    publisher.publishEvent(article);

    assertThat(row(article)).containsEntry("status", "failed").containsEntry("zone", null);
    assertThat(published(article))
        .singleElement()
        .extracting(event -> event.classification().status())
        .isEqualTo(ClassificationStatus.FAILED);
  }

  /** Moved from #148: an edited article is classified again, and red replaces the old green. */
  @Test
  void editedArticleIsClassifiedAgainAndRedReplacesGreen() {
    UUID articleId = UUID.randomUUID();
    when(classifier.classify(any()))
        .thenReturn(green(Topic.HOME))
        .thenReturn(Classification.classified(Zone.RED, List.of(Topic.OTHER), REASON, MODEL, AT));

    publisher.publishEvent(stored(articleId, 1));
    publisher.publishEvent(stored(articleId, 2));

    assertThat(row(stored(articleId, 2))).containsEntry("zone", "red");
    assertThat(events.stream(ArticleClassified.class))
        .filteredOn(event -> event.articleId().equals(articleId))
        .extracting(ArticleClassified::articleVersion, event -> event.classification().zone())
        .containsExactly(tuple(1, Zone.GREEN), tuple(2, Zone.RED));
  }

  @Test
  void sameArticleStoredTwiceChangesNothing() {
    ArticleStored article = stored(UUID.randomUUID(), 1);
    when(classifier.classify(any())).thenReturn(green(Topic.HOME));
    publisher.publishEvent(article);
    Map<String, Object> first = row(article);

    publisher.publishEvent(article);

    assertThat(rows(article)).isEqualTo(1L);
    assertThat(row(article)).isEqualTo(first);
    verify(classifier, times(1)).classify(any());
    assertThat(published(article)).hasSize(1);
  }

  /** The text is required to classify; without it the result is failed, never green. */
  @Test
  void articleWithoutTextIsStoredAsFailedWithoutAskingTheClassifier() {
    UUID articleId = UUID.randomUUID();
    ArticleStored article =
        new ArticleStored(
            articleId,
            1,
            "http://localhost:3000/artikel/" + articleId,
            "Rubrik",
            " ",
            "nyheter",
            "sv",
            AT,
            null,
            "correlation-" + articleId);

    publisher.publishEvent(article);

    assertThat(row(article)).containsEntry("status", "failed");
    verify(classifier, times(0)).classify(any());
  }

  private static ArticleStored stored(UUID articleId, int version) {
    return new ArticleStored(
        articleId,
        version,
        "http://localhost:3000/artikel/" + articleId,
        "Rubrik " + version,
        "Brödtext " + version + ".",
        "bostad",
        "sv",
        AT,
        version == 1 ? null : AT.plusSeconds(3600),
        "correlation-" + articleId + "-" + version);
  }

  private static Classification green(Topic... topics) {
    return Classification.classified(Zone.GREEN, List.of(topics), REASON, MODEL, AT);
  }

  private List<ArticleClassified> published(ArticleStored article) {
    return events.stream(ArticleClassified.class)
        .filter(event -> event.articleId().equals(article.articleId()))
        .filter(event -> event.articleVersion() == article.version())
        .toList();
  }

  private Map<String, Object> row(ArticleStored article) {
    return database
        .sql(
            """
            SELECT status, zone, reason_sv, reason_en, model, classified_at, correlation_id
            FROM classification WHERE article_id = :articleId AND article_version = :version
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .query()
        .singleRow();
  }

  private List<String> topics(ArticleStored article) {
    return database
        .sql(
            """
            SELECT topic FROM classification, unnest(topics) WITH ORDINALITY AS t(topic, position)
            WHERE article_id = :articleId AND article_version = :version ORDER BY position
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .query(String.class)
        .list();
  }

  private long rows(ArticleStored article) {
    return database
        .sql(
            """
            SELECT count(*) FROM classification
            WHERE article_id = :articleId AND article_version = :version
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .query(Long.class)
        .single();
  }
}
