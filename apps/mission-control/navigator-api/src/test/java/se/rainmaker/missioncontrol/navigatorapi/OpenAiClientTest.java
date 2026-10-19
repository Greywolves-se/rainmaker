package se.rainmaker.missioncontrol.navigatorapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import se.rainmaker.missioncontrol.navigatorapi.OpenAiException.Kind;

class OpenAiClientTest {

  @RegisterExtension
  static WireMockExtension openAi =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String PATH = "/v1/chat/completions";
  private static final String KEY = "sk-test-not-a-real-key";
  private static final String ANSWER_JSON =
      """
      {"choices": [{"message": {"role": "assistant", "content": "Two items are blocked."}}]}
      """;

  private OpenAiClient client(Duration timeout) {
    OpenAiSettings settings =
        new OpenAiSettings(
            URI.create(openAi.baseUrl()), "test-model", 300, timeout, Duration.ofMillis(1));
    return new OpenAiClient(settings, KEY);
  }

  private OpenAiClient client() {
    return client(Duration.ofSeconds(5));
  }

  @Test
  void sendsModelMessagesAndTokenLimitWithTheKeyAndReturnsTheAnswer() {
    openAi.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson(ANSWER_JSON)));

    String answer = client().complete("System rules.", "What is blocked?");

    assertThat(answer).isEqualTo("Two items are blocked.");
    openAi.verify(
        postRequestedFor(urlPathEqualTo(PATH))
            .withHeader("Authorization", equalTo("Bearer " + KEY))
            .withRequestBody(
                equalToJson(
                    """
                    {"model": "test-model",
                     "max_completion_tokens": 300,
                     "messages": [
                       {"role": "system", "content": "System rules."},
                       {"role": "user", "content": "What is blocked?"}]}
                    """)));
  }

  @Test
  void retriesOnceAfterServerError() {
    openAi.stubFor(
        post(urlPathEqualTo(PATH))
            .inScenario("flaky")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(502))
            .willSetStateTo("recovered"));
    openAi.stubFor(
        post(urlPathEqualTo(PATH))
            .inScenario("flaky")
            .whenScenarioStateIs("recovered")
            .willReturn(okJson(ANSWER_JSON)));

    assertThat(client().complete("s", "q")).isEqualTo("Two items are blocked.");
    openAi.verify(2, postRequestedFor(urlPathEqualTo(PATH)));
  }

  @Test
  void givesUpAfterTwoServerErrors() {
    openAi.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
    openAi.verify(2, postRequestedFor(urlPathEqualTo(PATH)));
  }

  @Test
  void doesNotRetryTimeoutSoTheLambdaStaysWithinItsTimeout() {
    openAi.stubFor(
        post(urlPathEqualTo(PATH)).willReturn(okJson(ANSWER_JSON).withFixedDelay(2_000)));

    assertThatThrownBy(() -> client(Duration.ofMillis(300)).complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class, e -> assertThat(e.kind()).isEqualTo(Kind.TIMEOUT));
    openAi.verify(1, postRequestedFor(urlPathEqualTo(PATH)));
  }

  @Test
  void doesNotRetryAnAuthenticationErrorAndNeverShowsTheKeyOrTheBody() {
    openAi.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(
                aResponse().withStatus(401).withBody("{\"error\": \"bad key " + KEY + "\"}")));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class,
            e -> {
              assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE);
              assertThat(e.getMessage()).contains("401").doesNotContain(KEY, "bad key");
            });
    openAi.verify(1, postRequestedFor(urlPathEqualTo(PATH)));
  }

  @Test
  void doesNotRetryWhenOpenAiSaysTheLimitIsReached() {
    openAi.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(429)));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
    openAi.verify(1, postRequestedFor(urlPathEqualTo(PATH)));
  }

  @Test
  void treatsAnAnswerWithoutTextAsUnavailable() {
    openAi.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson("{\"choices\": []}")));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
  }
}
