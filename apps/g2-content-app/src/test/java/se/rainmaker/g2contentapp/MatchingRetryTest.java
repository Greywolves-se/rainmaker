package se.rainmaker.g2contentapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.rainmaker.contentintent.api.ArticleClassified;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import se.rainmaker.matchingranking.api.CurrentSuggestions;
import se.rainmaker.matchingranking.api.Suggestion;
import se.rainmaker.matchingranking.database.MatchingRankingDatabaseConfiguration;

/**
 * How matching matches a green article again when G3's offers API was down (#248). G3 is a fake
 * (WireMock); the database is real (Testcontainers). The schedule is off: each test runs the retry
 * itself. A run takes at most 2 articles here.
 */
@SpringBootTest(
    properties = {
      "rainmaker.content-ingestion.feed.polling=false",
      "rainmaker.content-intent.retry.enabled=false",
      "rainmaker.matching-ranking.retry.enabled=false",
      "rainmaker.matching-ranking.retry.batch-size=2"
    })
@ExtendWith(OutputCaptureExtension.class)
class MatchingRetryTest {

  private static final String OFFERS = "/api/offers";
  private static final Instant AT = Instant.parse("2026-10-30T09:15:04Z");
  private static final Reason REASON = new Reason("Skäl.", "Reason.");

  /** Long before the retry interval: due for a retry. */
  private static final Instant LONG_AGO = Instant.parse("2026-01-01T00:00:00Z");

  private static final UUID TRAVEL_600 = UUID.fromString("01a1629f-c401-79e3-9779-9e3779b10001");
  private static final UUID TRAVEL_450 = UUID.fromString("01a1629f-c402-73c6-aef3-3c6ef3620002");
  private static final UUID HOME_450 = UUID.fromString("01a1629f-c407-7538-b453-538453d70007");

  private static final String G3_OFFERS =
      """
      {"offers": [
        {"offerId": "01a1629f-c401-79e3-9779-9e3779b10001", "category": "travel", "cpcOre": 600},
        {"offerId": "01a1629f-c402-73c6-aef3-3c6ef3620002", "category": "travel", "cpcOre": 450},
        {"offerId": "01a1629f-c407-7538-b453-538453d70007", "category": "home", "cpcOre": 450}
      ]}
      """;

  @RegisterExtension
  static WireMockExtension g3 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    registry.add("rainmaker.matching-ranking.offers.base-url", g3::baseUrl);
    registry.add("rainmaker.matching-ranking.offers.max-attempts", () -> "1");
  }

  @Autowired private ApplicationEventPublisher events;
  @Autowired private CurrentSuggestions suggestions;

  @Autowired
  @Qualifier("matchingRetry")
  private Runnable retry;

  @Autowired
  @Qualifier(MatchingRankingDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient matching;

  /** Other tests share the database: their articles count as just tried, so no run takes them. */
  @BeforeEach
  void leaveOtherTestsArticlesAlone() {
    matching.sql("UPDATE matched_article SET attempted_at = now()").update();
    g3.resetAll();
  }

  @Test
  void greenArticleMatchedWhileG3WasDownIsMatchedOnceG3Answers() {
    UUID article = UUID.randomUUID();
    g3IsDown();
    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));
    assertThat(suggestions.forArticle(article)).isEmpty();

    triedLongAgo(article, LONG_AGO);
    g3HasOffers();
    retry.run();

    List<Suggestion> current = suggestions.forArticle(article);
    assertThat(current).extracting(Suggestion::offerId).containsExactly(TRAVEL_600, TRAVEL_450);
    assertThat(current).extracting(Suggestion::articleVersion).containsOnly(1);
  }

  @Test
  void articleWaitsForTheNextIntervalWhileG3IsStillDown() {
    UUID article = UUID.randomUUID();
    g3IsDown();
    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));
    triedLongAgo(article, LONG_AGO);

    retry.run();
    assertThat(suggestions.forArticle(article)).isEmpty();

    // Tried just now, so the next run leaves it alone even though G3 is back.
    g3HasOffers();
    retry.run();
    assertThat(suggestions.forArticle(article)).isEmpty();

    triedLongAgo(article, LONG_AGO);
    retry.run();
    assertThat(suggestions.forArticle(article)).hasSize(2);
  }

  @Test
  void versionReplacedByNewerOneIsNotMatchedAgain() {
    UUID article = UUID.randomUUID();
    g3IsDown();
    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));
    events.publishEvent(
        classified(
            article,
            2,
            Classification.classified(Zone.RED, List.of(Topic.TRAVEL), REASON, "m", AT)));
    triedLongAgo(article, LONG_AGO);
    g3HasOffers();
    g3.resetRequests();

    retry.run();

    assertThat(suggestions.forArticle(article)).isEmpty();
    g3.verify(0, getRequestedFor(urlPathEqualTo(OFFERS)));
  }

  /** "Nothing fits" is a result, not an outage. */
  @Test
  void greenArticleWithoutFittingOffersIsNotMatchedAgain() {
    UUID article = UUID.randomUUID();
    g3HasOffers();
    events.publishEvent(classified(article, 1, green(Topic.CARS)));
    assertThat(suggestions.forArticle(article)).isEmpty();
    triedLongAgo(article, LONG_AGO);
    g3.resetRequests();

    retry.run();

    g3.verify(0, getRequestedFor(urlPathEqualTo(OFFERS)));
  }

  @Test
  void runTakesAtMostBatchSizeOldestFirst() {
    UUID oldest = UUID.randomUUID();
    UUID older = UUID.randomUUID();
    UUID newest = UUID.randomUUID();
    g3IsDown();
    for (UUID article : List.of(oldest, older, newest)) {
      events.publishEvent(classified(article, 1, green(Topic.HOME)));
    }
    triedLongAgo(oldest, LONG_AGO);
    triedLongAgo(older, LONG_AGO.plusSeconds(60));
    triedLongAgo(newest, LONG_AGO.plusSeconds(120));
    g3HasOffers();
    g3.resetRequests();

    retry.run();

    assertThat(suggestions.forArticle(oldest))
        .extracting(Suggestion::offerId)
        .containsExactly(HOME_450);
    assertThat(suggestions.forArticle(older))
        .extracting(Suggestion::offerId)
        .containsExactly(HOME_450);
    assertThat(suggestions.forArticle(newest)).isEmpty();
    // One call to G3 for the whole run, not one per article.
    g3.verify(1, getRequestedFor(urlPathEqualTo(OFFERS)));
  }

  @Test
  void logsIdVersionAndOutcome(CapturedOutput output) {
    UUID article = UUID.randomUUID();
    g3IsDown();
    events.publishEvent(classified(article, 3, green(Topic.TRAVEL)));
    triedLongAgo(article, LONG_AGO);
    g3HasOffers();

    retry.run();

    assertThat(output).contains("Article " + article + " version 3: 2 suggestions (matched again)");
  }

  private void g3IsDown() {
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(aResponse().withStatus(503)));
  }

  private void g3HasOffers() {
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(okJson(G3_OFFERS)));
  }

  private void triedLongAgo(UUID article, Instant at) {
    matching
        .sql("UPDATE matched_article SET attempted_at = :at WHERE article_id = :article")
        .param("at", at.atOffset(ZoneOffset.UTC))
        .param("article", article)
        .update();
  }

  private static ArticleClassified classified(
      UUID article, int version, Classification classification) {
    return new ArticleClassified(
        article,
        version,
        "http://localhost:3000/artikel/" + article,
        "Rubrik",
        classification,
        "correlation-" + article);
  }

  private static Classification green(Topic... topics) {
    return Classification.classified(Zone.GREEN, List.of(topics), REASON, "m", AT);
  }
}
