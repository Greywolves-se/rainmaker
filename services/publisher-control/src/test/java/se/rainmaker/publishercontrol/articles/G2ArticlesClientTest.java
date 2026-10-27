package se.rainmaker.publishercontrol.articles;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** G3 reads G2's changed articles (#150, contract 7.1), with timeouts and retries. */
class G2ArticlesClientTest {

  private static final String ARTICLES = "/api/articles";
  private static final Instant SINCE = Instant.parse("2026-10-30T09:00:00Z");
  private static final String NO_CHANGES =
      """
      {"articles": [], "nextSince": "2026-10-30T09:15:05Z"}
      """;

  @RegisterExtension
  static WireMockExtension g2 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private G2ArticlesClient client() {
    return new G2ArticlesClient(
        new ArticlesProperties(
            URI.create(g2.baseUrl()),
            Duration.ofSeconds(1),
            Duration.ofMillis(300),
            3,
            Duration.ofMillis(10),
            Duration.ofSeconds(5),
            true));
  }

  @Test
  void asksForChangesSinceAndReadsNextSince() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(okJson(NO_CHANGES)));

    ArticleChanges changes = client().changedSince(SINCE);

    assertThat(changes.articles()).isEmpty();
    assertThat(changes.nextSince()).isEqualTo(Instant.parse("2026-10-30T09:15:05Z"));
    g2.verify(
        getRequestedFor(urlPathEqualTo(ARTICLES))
            .withQueryParam("since", equalTo("2026-10-30T09:00:00Z")));
  }

  @Test
  void retriesServerErrorWithBackoff() {
    g2.stubFor(
        get(urlPathEqualTo(ARTICLES))
            .inScenario("restart")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("up"));
    g2.stubFor(
        get(urlPathEqualTo(ARTICLES))
            .inScenario("restart")
            .whenScenarioStateIs("up")
            .willReturn(okJson(NO_CHANGES)));

    assertThat(client().changedSince(SINCE).articles()).isEmpty();
    g2.verify(2, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  @Test
  void givesUpAfterTheLastAttempt() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client().changedSince(SINCE))
        .isInstanceOf(ArticlesUnavailableException.class)
        .hasMessageContaining("500");
    g2.verify(3, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  /** A client error will not go away by asking again. */
  @Test
  void doesNotRetryClientError() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> client().changedSince(SINCE))
        .isInstanceOf(ArticlesUnavailableException.class)
        .hasMessageContaining("400");
    g2.verify(1, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  /** Brand safety: a zone the contract doesn't know is never stored as something else. */
  @Test
  void refusesAnswerWithUnknownZone() {
    g2.stubFor(
        get(urlPathEqualTo(ARTICLES))
            .willReturn(
                okJson(
                    """
                    {"articles": [{"articleId": "0192f3c2-0000-7000-8000-000000000001",
                      "version": 1, "url": "http://localhost:3000/artikel/x", "headline": "X",
                      "classification": {"status": "classified", "zone": "blue"},
                      "suggestions": [], "changedAt": "2026-10-30T09:15:05Z",
                      "correlationId": "c0a1"}],
                     "nextSince": "2026-10-30T09:15:05Z"}
                    """)));

    assertThatThrownBy(() -> client().changedSince(SINCE))
        .isInstanceOf(ArticlesUnavailableException.class)
        .hasMessageContaining("classification.zone");
  }

  @Test
  void refusesAnswerWithoutArticleList() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(okJson("{}")));

    assertThatThrownBy(() -> client().changedSince(SINCE))
        .isInstanceOf(ArticlesUnavailableException.class);
  }
}
