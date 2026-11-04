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

/**
 * G3 reads a snapshot of G2's recent articles (#150, contract 7.1), with timeouts and retries, and
 * one article on the reader path with a short time budget and a circuit breaker (#230).
 */
class G2ArticlesClientTest {

  private static final String ARTICLES = "/api/articles";
  private static final String NO_ARTICLES =
      """
      {"articles": []}
      """;

  @RegisterExtension
  static WireMockExtension g2 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String ARTICLE = "http://localhost:3000/artikel/badrummet";

  /**
   * Generous, so the reader client's first request, which also warms up WireMock and the HTTP
   * client, isn't cut off on a slow machine (#211); {@link #readerCallGivesUpAfterOneAttempt} uses
   * a short one.
   */
  private static final Duration READER_READ_TIMEOUT = Duration.ofSeconds(5);

  private static final ArticlesProperties.CircuitBreaker NEVER_OPENS =
      new ArticlesProperties.CircuitBreaker(10, 10, 50, Duration.ofMinutes(1));

  /** Opens after two failed calls of the last two, for a minute. */
  private static final ArticlesProperties.CircuitBreaker OPENS_QUICKLY =
      new ArticlesProperties.CircuitBreaker(2, 2, 50, Duration.ofMinutes(1));

  private final TestClock clock = new TestClock(Instant.parse("2026-11-02T18:00:00Z"));

  private G2ArticlesClient client() {
    return client(READER_READ_TIMEOUT, NEVER_OPENS);
  }

  private G2ArticlesClient client(
      Duration readerReadTimeout, ArticlesProperties.CircuitBreaker breaker) {
    return new G2ArticlesClient(
        new ArticlesProperties(
            URI.create(g2.baseUrl()),
            Duration.ofSeconds(1),
            Duration.ofMillis(300),
            3,
            Duration.ofMillis(10),
            Duration.ofSeconds(5),
            100,
            new ArticlesProperties.Reader(Duration.ofSeconds(1), readerReadTimeout, breaker),
            true),
        clock);
  }

  @Test
  void asksForTheRecentArticles() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(okJson(NO_ARTICLES)));

    assertThat(client().recent().articles()).isEmpty();
    g2.verify(getRequestedFor(urlPathEqualTo(ARTICLES)).withQueryParam("recent", equalTo("100")));
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
            .willReturn(okJson(NO_ARTICLES)));

    assertThat(client().recent().articles()).isEmpty();
    g2.verify(2, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  @Test
  void givesUpAfterTheLastAttempt() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client().recent())
        .isInstanceOf(ArticlesUnavailableException.class)
        .hasMessageContaining("500");
    g2.verify(3, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  /** A client error will not go away by asking again. */
  @Test
  void doesNotRetryClientError() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> client().recent())
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
                      "publishedAt": "2026-10-10T05:45:00Z", "suggestions": [],
                      "correlationId": "c0a1"}]}
                    """)));

    assertThatThrownBy(() -> client().recent())
        .isInstanceOf(ArticlesUnavailableException.class)
        .hasMessageContaining("classification.zone");
  }

  @Test
  void refusesAnswerWithoutArticleList() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(okJson("{}")));

    assertThatThrownBy(() -> client().recent()).isInstanceOf(ArticlesUnavailableException.class);
  }

  // ---------------------------------------------------------------- the reader path (#230)

  /** One attempt only: the reader has already been given up on by G1 after its own timeout. */
  @Test
  void readerCallGivesUpAfterOneAttempt() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(okJson(NO_ARTICLES).withFixedDelay(1_000)));

    assertThatThrownBy(() -> client(Duration.ofMillis(300), NEVER_OPENS).current(ARTICLE, null))
        .isInstanceOf(ArticlesUnavailableException.class);
    g2.verify(1, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  @Test
  void readerCallIsNotRetriedOnServerError() {
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> client().current(ARTICLE, null))
        .isInstanceOf(ArticlesUnavailableException.class)
        .hasMessageContaining("503");
    g2.verify(1, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  /** While G2 keeps failing, the reader path stops asking it and answers at once. */
  @Test
  void readerCircuitOpensAndThenG2IsNotCalled() {
    G2ArticlesClient client = client(READER_READ_TIMEOUT, OPENS_QUICKLY);
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(500)));
    failTwice(client);

    assertThatThrownBy(() -> client.current(ARTICLE, null))
        .isInstanceOf(ArticlesUnavailableException.class)
        .hasMessageContaining("circuit is open");
    g2.verify(2, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  @Test
  void readerCircuitLetsTrialCallThroughAfterOpenForAndClosesOnSuccess() {
    G2ArticlesClient client = client(READER_READ_TIMEOUT, OPENS_QUICKLY);
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(500)));
    failTwice(client);
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(okJson(NO_ARTICLES)));

    // The trial call is allowed only once the open time has passed, not at exactly one minute.
    clock.advance(Duration.ofMinutes(1).plusSeconds(1));

    assertThat(client.current(ARTICLE, null)).isEmpty();
    assertThat(client.current(ARTICLE, null)).isEmpty();
    g2.verify(4, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  /** A bad request says nothing about G2's health, so it never opens the circuit. */
  @Test
  void clientErrorsNeverOpenTheReaderCircuit() {
    G2ArticlesClient client = client(READER_READ_TIMEOUT, OPENS_QUICKLY);
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(400)));
    failTwice(client);

    assertThatThrownBy(() -> client.current(ARTICLE, null))
        .isInstanceOf(ArticlesUnavailableException.class)
        .hasMessageContaining("400");
    g2.verify(3, getRequestedFor(urlPathEqualTo(ARTICLES)));
  }

  /** The poll keeps its own timeouts and retries, and isn't stopped by the reader's circuit. */
  @Test
  void pollStillRetriesWhileTheReaderCircuitIsOpen() {
    G2ArticlesClient client = client(READER_READ_TIMEOUT, OPENS_QUICKLY);
    g2.stubFor(get(urlPathEqualTo(ARTICLES)).willReturn(aResponse().withStatus(500)));
    failTwice(client);
    g2.resetAll();
    g2.stubFor(
        get(urlPathEqualTo(ARTICLES))
            .inScenario("slow restart")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("up"));
    g2.stubFor(
        get(urlPathEqualTo(ARTICLES))
            .inScenario("slow restart")
            .whenScenarioStateIs("up")
            .willReturn(okJson(NO_ARTICLES)));

    assertThat(client.recent().articles()).isEmpty();
    g2.verify(2, getRequestedFor(urlPathEqualTo(ARTICLES)));
    assertThatThrownBy(() -> client.current(ARTICLE, null)).hasMessageContaining("circuit is open");
  }

  private static void failTwice(G2ArticlesClient client) {
    for (int call = 0; call < 2; call++) {
      assertThatThrownBy(() -> client.current(ARTICLE, null))
          .isInstanceOf(ArticlesUnavailableException.class);
    }
  }
}
