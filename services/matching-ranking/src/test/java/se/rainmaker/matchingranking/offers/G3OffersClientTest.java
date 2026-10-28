package se.rainmaker.matchingranking.offers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import se.rainmaker.matchingranking.ranking.OfferForMatching;

/** Matching reads the offers from G3's API (#145, contract 7.2), with timeouts and retries. */
class G3OffersClientTest {

  private static final String OFFERS = "/api/offers";

  /** The offers API's answer, with every field G3 sends; matching only uses three of them. */
  private static final String ONE_OFFER =
      """
      {"offers": [{
        "offerId": "01a1629f-c401-79e3-9779-9e3779b10001",
        "advertiser": "Nordsol Resor",
        "title": "Höstresa till Mallorca – sju nätter",
        "text": "Flyg och hotell med frukost, avresa från Vintra.",
        "category": "travel",
        "destinationUrl": "http://localhost:8083/landing/01a1629f-c401-79e3-9779-9e3779b10001",
        "cpcOre": 600
      }]}
      """;

  @RegisterExtension
  static WireMockExtension g3 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private G3OffersClient client() {
    return new G3OffersClient(
        new OffersProperties(
            URI.create(g3.baseUrl()),
            Duration.ofSeconds(1),
            Duration.ofMillis(300),
            3,
            Duration.ofMillis(10)));
  }

  @Test
  void readsCategoryAndCpcOfEveryOffer() {
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(okJson(ONE_OFFER)));

    assertThat(client().offers())
        .containsExactly(
            new OfferForMatching(
                UUID.fromString("01a1629f-c401-79e3-9779-9e3779b10001"), "travel", 600));
  }

  @Test
  void retriesServerErrorWithBackoff() {
    g3.stubFor(
        get(urlPathEqualTo(OFFERS))
            .inScenario("restart")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("up"));
    g3.stubFor(
        get(urlPathEqualTo(OFFERS))
            .inScenario("restart")
            .whenScenarioStateIs("up")
            .willReturn(okJson(ONE_OFFER)));

    assertThat(client().offers()).hasSize(1);
    g3.verify(2, getRequestedFor(urlPathEqualTo(OFFERS)));
  }

  @Test
  void givesUpAfterTheLastAttempt() {
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client().offers())
        .isInstanceOf(OffersUnavailableException.class)
        .hasMessageContaining("500");
    g3.verify(3, getRequestedFor(urlPathEqualTo(OFFERS)));
  }

  @Test
  void retriesTimeoutThenGivesUp() {
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(okJson(ONE_OFFER).withFixedDelay(1_000)));

    assertThatThrownBy(() -> client().offers()).isInstanceOf(OffersUnavailableException.class);
    g3.verify(3, getRequestedFor(urlPathEqualTo(OFFERS)));
  }

  /** A client error will not go away by asking again. */
  @Test
  void doesNotRetryClientError() {
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> client().offers())
        .isInstanceOf(OffersUnavailableException.class)
        .hasMessageContaining("404");
    g3.verify(1, getRequestedFor(urlPathEqualTo(OFFERS)));
  }

  @Test
  void refusesAnswerWithoutOfferList() {
    g3.stubFor(get(urlPathEqualTo(OFFERS)).willReturn(okJson("{}")));

    assertThatThrownBy(() -> client().offers()).isInstanceOf(OffersUnavailableException.class);
  }
}
