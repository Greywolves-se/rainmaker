package se.rainmaker.widgetdelivery.links;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * S1 reads an article's approved links from G3 (#151, contract 7.3), with short timeouts, one retry
 * and a circuit breaker, and fails closed: no answer from G3 means no links.
 */
class G3ApprovedLinksClientTest {

  private static final String API = "/api/approved-links";
  private static final String ARTICLE =
      "http://localhost:3000/artikel/badrumsrenovering-experternas-rad";

  /** G3's answer exactly as contract 7.3 shows it; G3's own tests can read the same file. */
  private static final String CONTRACT_EXAMPLE = ContractExamples.read("7.3-approved-links.json");

  @RegisterExtension
  static WireMockExtension g3 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  /**
   * Generous, so the class's first request, which also warms up WireMock and the HTTP client, isn't
   * cut off on a slow machine; {@link #timeoutMeansUnavailable} uses a short one.
   */
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  private static final ApprovedLinksProperties.CircuitBreaker NEVER_OPENS =
      new ApprovedLinksProperties.CircuitBreaker(10, 10, 50, Duration.ofMinutes(1));

  private static G3ApprovedLinksClient client() {
    return client(READ_TIMEOUT, NEVER_OPENS);
  }

  private static G3ApprovedLinksClient client(ApprovedLinksProperties.CircuitBreaker breaker) {
    return client(READ_TIMEOUT, breaker);
  }

  private static G3ApprovedLinksClient client(
      Duration readTimeout, ApprovedLinksProperties.CircuitBreaker breaker) {
    return new G3ApprovedLinksClient(
        new ApprovedLinksProperties(
            URI.create(g3.baseUrl()),
            Duration.ofSeconds(1),
            readTimeout,
            2,
            Duration.ofMillis(10),
            breaker));
  }

  @Test
  void readsTheArticleAndItsLinksAsTheContractShowsThem() {
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(okJson(CONTRACT_EXAMPLE)));

    ApprovedLinks answer = client().find(ARTICLE, "c0a1-test");

    assertThat(answer.articleId()).isEqualTo(id("0192f3c2-5e1a-7b10-8a3c-4d5e6f708192"));
    assertThat(answer.links())
        .containsExactly(
            new ApprovedLink(
                id("0192f3d0-2b4c-7d10-9e8f-a1b2c3d4e5f6"),
                1,
                "Vintra Kakel & Bad",
                "Gratis hembesök för badrumsoffert",
                "Vi mäter upp och ger fast pris inom en vecka.",
                "http://localhost:8083/landing/0192f3d0-2b4c-7d10-9e8f-a1b2c3d4e5f6"));
  }

  /** The address is one query value, even with characters that mean something in a URL. */
  @Test
  void sendsTheArticleAddressAndTheCorrelationId() {
    String article = "http://localhost:3000/artikel/a?b=c&d=e f#g";
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(okJson(CONTRACT_EXAMPLE)));

    client().find(article, "c0a1-test");

    g3.verify(
        getRequestedFor(urlPathEqualTo(API))
            .withQueryParam("url", equalTo(article))
            .withHeader("X-Correlation-Id", equalTo("c0a1-test")));
  }

  @Test
  void sendsNoCorrelationIdWhenThereIsNone() {
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(okJson(CONTRACT_EXAMPLE)));

    client().find(ARTICLE, null);

    g3.verify(getRequestedFor(urlPathEqualTo(API)).withHeader("X-Correlation-Id", absent()));
  }

  @Test
  void unknownArticleHasNoIdAndNoLinks() {
    g3.stubFor(
        get(urlPathEqualTo(API))
            .willReturn(okJson("{\"articleId\": null, \"articleUrl\": null, \"links\": []}")));

    ApprovedLinks answer = client().find(ARTICLE, null);

    assertThat(answer.articleId()).isNull();
    assertThat(answer.links()).isEmpty();
  }

  /** A link G3 sent without one of its fields is left out, never shown half-filled. */
  @Test
  void leavesOutLinkWithMissingField() {
    String withoutTitle = CONTRACT_EXAMPLE.replace("\"title\"", "\"heading\"");
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(okJson(withoutTitle)));

    assertThat(client().find(ARTICLE, null).links()).isEmpty();
  }

  @Test
  void retriesServerErrorOnce() {
    g3.stubFor(
        get(urlPathEqualTo(API))
            .inScenario("restart")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("up"));
    g3.stubFor(
        get(urlPathEqualTo(API))
            .inScenario("restart")
            .whenScenarioStateIs("up")
            .willReturn(okJson(CONTRACT_EXAMPLE)));

    assertThat(client().find(ARTICLE, null).links()).hasSize(1);
    g3.verify(2, getRequestedFor(urlPathEqualTo(API)));
  }

  /** G3 answers 503 when G2 can't say whether the article is green: no links (fail closed). */
  @Test
  void noAnswerAfterTheLastAttemptMeansUnavailable() {
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> client().find(ARTICLE, null))
        .isInstanceOf(ApprovedLinksUnavailableException.class)
        .hasMessageContaining("503");
    g3.verify(2, getRequestedFor(urlPathEqualTo(API)));
  }

  /**
   * The only test that waits on real time: nothing but a slow answer proves the read timeout is
   * set. The margin is wide (a 1 s delay against 300 ms), so it doesn't flake (#224).
   */
  @Test
  void timeoutMeansUnavailable() {
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(okJson(CONTRACT_EXAMPLE).withFixedDelay(1_000)));

    assertThatThrownBy(() -> client(Duration.ofMillis(300), NEVER_OPENS).find(ARTICLE, null))
        .isInstanceOf(ApprovedLinksUnavailableException.class);
    g3.verify(2, getRequestedFor(urlPathEqualTo(API)));
  }

  /** A dropped connection may go away, so it is retried once like a timeout, without waiting. */
  @Test
  void droppedConnectionIsRetriedThenUnavailable() {
    g3.stubFor(
        get(urlPathEqualTo(API)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThatThrownBy(() -> client().find(ARTICLE, null))
        .isInstanceOf(ApprovedLinksUnavailableException.class);
    g3.verify(2, getRequestedFor(urlPathEqualTo(API)));
  }

  /** G3 not running at all: port 1 refuses every connection. */
  @Test
  void g3NotRunningMeansUnavailable() {
    G3ApprovedLinksClient down =
        new G3ApprovedLinksClient(
            new ApprovedLinksProperties(
                URI.create("http://127.0.0.1:1"),
                Duration.ofMillis(250),
                READ_TIMEOUT,
                2,
                Duration.ofMillis(10),
                NEVER_OPENS));

    assertThatThrownBy(() -> down.find(ARTICLE, null))
        .isInstanceOf(ApprovedLinksUnavailableException.class);
  }

  /** A client error won't go away by asking again. */
  @Test
  void doesNotRetryClientError() {
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> client().find(ARTICLE, null))
        .isInstanceOf(ApprovedLinksUnavailableException.class)
        .hasMessageContaining("400");
    g3.verify(1, getRequestedFor(urlPathEqualTo(API)));
  }

  @Test
  void answerWithoutLinkListMeansUnavailable() {
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(okJson("{\"articleId\": null}")));

    assertThatThrownBy(() -> client().find(ARTICLE, null))
        .isInstanceOf(ApprovedLinksUnavailableException.class);
  }

  @Test
  void answerThatIsNotJsonMeansUnavailable() {
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(okJson("<html>")));

    assertThatThrownBy(() -> client().find(ARTICLE, null))
        .isInstanceOf(ApprovedLinksUnavailableException.class);
  }

  /**
   * While G3 keeps failing, hovers stop waiting for its timeouts: the circuit opens and the next
   * request is unavailable at once, without calling G3.
   */
  @Test
  void stopsCallingG3WhileItKeepsFailing() {
    G3ApprovedLinksClient client =
        client(new ApprovedLinksProperties.CircuitBreaker(2, 2, 50, Duration.ofMinutes(1)));
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(aResponse().withStatus(503)));

    for (int request = 0; request < 2; request++) {
      assertThatThrownBy(() -> client.find(ARTICLE, null))
          .isInstanceOf(ApprovedLinksUnavailableException.class);
    }
    g3.resetRequests();

    assertThatThrownBy(() -> client.find(ARTICLE, null))
        .isInstanceOf(ApprovedLinksUnavailableException.class)
        .hasMessageContaining("circuit");
    g3.verify(0, getRequestedFor(urlPathEqualTo(API)));
  }

  /** A bad request says nothing about G3's health, so it never opens the circuit. */
  @Test
  void clientErrorsNeverOpenTheCircuit() {
    G3ApprovedLinksClient client =
        client(new ApprovedLinksProperties.CircuitBreaker(2, 2, 50, Duration.ofMinutes(1)));
    g3.stubFor(get(urlPathEqualTo(API)).willReturn(aResponse().withStatus(400)));

    for (int request = 0; request < 3; request++) {
      assertThatThrownBy(() -> client.find(ARTICLE, null))
          .isInstanceOf(ApprovedLinksUnavailableException.class);
    }

    g3.verify(3, getRequestedFor(urlPathEqualTo(API)));
  }

  private static UUID id(String text) {
    return UUID.fromString(text);
  }
}
