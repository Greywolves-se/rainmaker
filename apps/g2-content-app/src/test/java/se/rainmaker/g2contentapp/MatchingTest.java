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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.rainmaker.contentintent.api.ArticleClassified;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import se.rainmaker.matchingranking.api.CurrentSuggestions;
import se.rainmaker.matchingranking.api.Suggestion;

/**
 * Matching in G2 (#149): a classified article gets suggestions from G3's offers (a fake G3 here),
 * and loses them when it is no longer green. The database is real (Testcontainers).
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class MatchingTest {

  private static final String OFFERS = "/api/offers";
  private static final Instant AT = Instant.parse("2026-10-30T09:15:04Z");
  private static final Reason REASON = new Reason("Skäl.", "Reason.");

  private static final UUID TRAVEL_600 = UUID.fromString("01a1629f-c401-79e3-9779-9e3779b10001");
  private static final UUID TRAVEL_450 = UUID.fromString("01a1629f-c402-73c6-aef3-3c6ef3620002");
  private static final UUID HOME_450 = UUID.fromString("01a1629f-c407-7538-b453-538453d70007");

  /** Part of G3's answer (contract 7.2): two travel offers, one home and one car offer. */
  private static final String G3_OFFERS =
      """
      {"offers": [
        {"offerId": "01a1629f-c401-79e3-9779-9e3779b10001", "category": "travel", "cpcOre": 600},
        {"offerId": "01a1629f-c402-73c6-aef3-3c6ef3620002", "category": "travel", "cpcOre": 450},
        {"offerId": "01a1629f-c407-7538-b453-538453d70007", "category": "home", "cpcOre": 450},
        {"offerId": "01a1629f-c408-7f1b-8bcd-f1bbcd880008", "category": "cars", "cpcOre": 500}
      ]}
      """;

  @RegisterExtension
  static WireMockExtension g3 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    registry.add("rainmaker.matching-ranking.offers.base-url", g3::baseUrl);
    registry.add("rainmaker.matching-ranking.offers.backoff", () -> "10ms");
  }

  @Autowired private ApplicationEventPublisher events;
  @Autowired private CurrentSuggestions suggestions;

  @BeforeEach
  void g3HasOffers() {
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(okJson(G3_OFFERS)));
  }

  @Test
  void greenTravelArticleGetsTheTravelOffers() {
    UUID article = UUID.randomUUID();

    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));

    List<Suggestion> current = suggestions.forArticle(article);
    assertThat(current).extracting(Suggestion::offerId).containsExactly(TRAVEL_600, TRAVEL_450);
    assertThat(current).extracting(Suggestion::rank).containsExactly(1, 2);
    assertThat(current)
        .allSatisfy(
            suggestion -> {
              assertThat(suggestion.articleVersion()).isEqualTo(1);
              assertThat(suggestion.matchedTopics()).containsExactly("travel");
              assertThat(suggestion.suggestionId().version()).isEqualTo(7);
            });
  }

  static Stream<Arguments> notGreen() {
    return Stream.of(
        Arguments.of(
            "red", Classification.classified(Zone.RED, List.of(Topic.TRAVEL), REASON, "m", AT)),
        Arguments.of(
            "yellow",
            Classification.classified(Zone.YELLOW, List.of(Topic.TRAVEL), REASON, "m", AT)),
        Arguments.of("failed", Classification.failed("m", AT)),
        Arguments.of("unclassified", Classification.unclassified()));
  }

  /** D-4: everything that isn't classified + green gets no suggestions, and G3 isn't asked. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("notGreen")
  void articleThatIsNotGreenGetsNoSuggestions(String name, Classification classification) {
    UUID article = UUID.randomUUID();

    events.publishEvent(classified(article, 1, classification));

    assertThat(suggestions.forArticle(article)).isEmpty();
    g3.verify(0, getRequestedFor(urlPathEqualTo(OFFERS)));
  }

  @Test
  void articleThatTurnsRedAfterMatchingLosesItsSuggestions() {
    UUID article = UUID.randomUUID();
    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));
    assertThat(suggestions.forArticle(article)).hasSize(2);

    // Classified again, e.g. after a retry: same version, now red.
    events.publishEvent(
        classified(
            article,
            1,
            Classification.classified(Zone.RED, List.of(Topic.OTHER), REASON, "m", AT)));

    assertThat(suggestions.forArticle(article)).isEmpty();
  }

  /** D-5: a new version replaces the whole set, here with nothing, since it turned red. */
  @Test
  void newVersionThatTurnsRedLosesTheEarlierSuggestions() {
    UUID article = UUID.randomUUID();
    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));

    events.publishEvent(
        classified(
            article,
            2,
            Classification.classified(Zone.RED, List.of(Topic.OTHER), REASON, "m", AT)));

    assertThat(suggestions.forArticle(article)).isEmpty();
  }

  @Test
  void sameEventTwiceChangesNothing() {
    UUID article = UUID.randomUUID();
    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));
    List<Suggestion> first = suggestions.forArticle(article);

    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));

    assertThat(suggestions.forArticle(article)).isEqualTo(first);
  }

  @Test
  void olderVersionIsIgnored() {
    UUID article = UUID.randomUUID();
    events.publishEvent(classified(article, 2, green(Topic.HOME)));

    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));

    assertThat(suggestions.forArticle(article))
        .extracting(Suggestion::offerId)
        .containsExactly(HOME_450);
  }

  @Test
  void whenG3IsDownNothingNewIsStoredAndNewVersionLosesItsOldSuggestions(CapturedOutput output) {
    UUID article = UUID.randomUUID();
    events.publishEvent(classified(article, 1, green(Topic.TRAVEL)));
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(aResponse().withStatus(503)));

    events.publishEvent(classified(article, 2, green(Topic.TRAVEL)));

    assertThat(suggestions.forArticle(article)).isEmpty();
    assertThat(output).contains("No suggestions for article " + article);
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
