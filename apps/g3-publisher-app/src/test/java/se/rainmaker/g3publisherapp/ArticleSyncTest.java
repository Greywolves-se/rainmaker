package se.rainmaker.g3publisherapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static se.rainmaker.g3publisherapp.FakeG2Articles.ARTICLES;
import static se.rainmaker.g3publisherapp.FakeG2Articles.HOME_OFFER;
import static se.rainmaker.g3publisherapp.FakeG2Articles.TRAVEL_OFFER;
import static se.rainmaker.g3publisherapp.FakeG2Articles.article;
import static se.rainmaker.g3publisherapp.FakeG2Articles.suggestion;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.rainmaker.publishercontrol.articles.ArticleSync;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;

/**
 * G3 copies G2's articles and suggestions for the editor (#150, contract 5 and 7.1), from a fake
 * G2. The database is real (Testcontainers).
 */
@SpringBootTest(properties = "rainmaker.publisher-control.articles.polling=false")
class ArticleSyncTest {

  @RegisterExtension
  static WireMockExtension g2 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    registry.add("rainmaker.publisher-control.articles.base-url", g2::baseUrl);
    registry.add("rainmaker.publisher-control.articles.backoff", () -> "10ms");
  }

  @Autowired private ArticleSync sync;

  @Autowired
  @Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient database;

  @Test
  void classifiedAndMatchedArticleIsStoredWithItsSuggestions() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    UUID travel = UUID.randomUUID();

    g2Sends(
        article(
            article,
            1,
            "green",
            suggestion(home, HOME_OFFER, 1),
            suggestion(travel, TRAVEL_OFFER, 2)));

    assertThat(column("headline", article)).isEqualTo("Badrummet som håller i 40 år");
    assertThat(column("reason_en", article)).isEqualTo("Practical advice.");
    assertThat(statuses(article)).isEqualTo(home + "=suggested," + travel + "=suggested");
  }

  /** D-4: a red article never keeps a suggestion, even if G2 sends one by mistake. */
  @Test
  void redArticleGetsNoSuggestionsEvenIfSomeAreSent() {
    UUID article = UUID.randomUUID();

    g2Sends(article(article, 1, "red", suggestion(UUID.randomUUID(), HOME_OFFER, 1)));

    assertThat(column("zone", article)).isEqualTo("red");
    assertThat(statuses(article)).isEmpty();
  }

  @Test
  void articleThatTurnsRedLosesItsSuggestions() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));

    // Classified again, same version, now red: G2 sends an empty set.
    g2Sends(article(article, 1, "red"));

    assertThat(statuses(article)).isEqualTo(home + "=withdrawn");
  }

  /** D-5: a new version replaces the whole set; the earlier suggestions are withdrawn. */
  @Test
  void newVersionWithdrawsTheEarlierSet() {
    UUID article = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(first, HOME_OFFER, 1)));

    g2Sends(article(article, 2, "green", suggestion(second, HOME_OFFER, 1)));

    assertThat(statuses(article)).isEqualTo(first + "=withdrawn," + second + "=suggested");
  }

  /** G2 may send the same article twice (contract 7.1). */
  @Test
  void sameAnswerTwiceChangesNothing() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    String answer = article(article, 1, "green", suggestion(home, HOME_OFFER, 1));
    g2Sends(answer);

    g2Sends(answer);

    assertThat(statuses(article)).isEqualTo(home + "=suggested");
  }

  @Test
  void olderVersionIsIgnored() {
    UUID article = UUID.randomUUID();
    UUID current = UUID.randomUUID();
    g2Sends(article(article, 2, "green", suggestion(current, HOME_OFFER, 1)));

    g2Sends(article(article, 1, "green", suggestion(UUID.randomUUID(), TRAVEL_OFFER, 1)));

    assertThat(statuses(article)).isEqualTo(current + "=suggested");
  }

  @Test
  void pollAsksForTheRecentArticles() {
    g2Sends();

    g2.verify(getRequestedFor(urlPathEqualTo(ARTICLES)).withQueryParam("recent", equalTo("100")));
  }

  /**
   * No bookmark: a reclassification of the same version, which changes nothing but the zone, is
   * caught by the next snapshot.
   */
  @Test
  void reclassificationOfTheSameVersionIsCaughtByTheNextPoll() {
    UUID article = UUID.randomUUID();
    g2Sends(article(article, 1, "yellow"));

    g2Sends(article(article, 1, "red"));

    assertThat(column("zone", article)).isEqualTo("red");
  }

  @Test
  void whenG2IsDownNothingChanges() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(503)));

    sync.pollOnce();

    assertThat(statuses(article)).isEqualTo(home + "=suggested");
  }

  private void g2Sends(String... articles) {
    FakeG2Articles.g2Sends(g2, sync, articles);
  }

  private String statuses(UUID article) {
    return FakeG2Articles.statuses(database, article);
  }

  private String column(String column, UUID article) {
    return database
        .sql("SELECT " + column + " FROM article WHERE article_id = ?")
        .param(article)
        .query(String.class)
        .single();
  }
}
