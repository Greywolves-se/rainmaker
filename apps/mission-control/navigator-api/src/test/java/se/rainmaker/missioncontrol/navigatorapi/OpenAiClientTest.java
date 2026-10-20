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

  private static final String PATH = "/v1/responses";
  private static final String KEY = "sk-test-not-a-real-key";
  private static final String ANSWER_JSON =
      responseWithText("{\\\"answer\\\": \\\"Two items are blocked.\\\"}");

  /** A Responses API answer whose message has one output_text item. */
  private static String responseWithText(String escapedText) {
    return "{\"status\": \"completed\", \"output\": [{\"type\": \"message\", \"content\": "
        + "[{\"type\": \"output_text\", \"text\": \""
        + escapedText
        + "\"}]}]}";
  }

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
  void asksTheResponsesApiForStrictJsonAndReturnsTheAnswer() {
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
                     "instructions": "System rules.",
                     "input": "What is blocked?",
                     "max_output_tokens": 300,
                     "text": {"format": {
                       "type": "json_schema",
                       "name": "navigator_answer",
                       "strict": true,
                       "schema": {
                         "type": "object",
                         "additionalProperties": false,
                         "properties": {"answer": {"type": "string"}},
                         "required": ["answer"]}}}}
                    """)));
  }

  @Test
  void treatsRefusalAsUnavailable() {
    openAi.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(
                okJson(
                    "{\"status\": \"completed\", \"output\": [{\"type\": \"message\", \"content\": "
                        + "[{\"type\": \"refusal\", \"refusal\": \"No.\"}]}]}")));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class, e -> assertThat(e).hasMessage("OpenAI refused to answer"));
  }

  @Test
  void treatsIncompleteAnswerAsUnavailable() {
    openAi.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(okJson("{\"status\": \"incomplete\", \"output\": []}")));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class,
            e -> assertThat(e).hasMessage("OpenAI stopped before the answer was complete"));
  }

  @Test
  void treatsTextThatIsNotTheAgreedJsonAsUnavailable() {
    openAi.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson(responseWithText("just prose"))));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
  }

  @Test
  void treatsBodyThatIsNotJsonAsUnavailable() {
    openAi.stubFor(
        post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("<html>")));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
  }

  @Test
  void saysWhenTheQuotaIsUsedUpWithoutRetrying() {
    openAi.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withBody(
                        "{\"error\": {\"code\": \"insufficient_quota\", \"message\": \"key "
                            + KEY
                            + "\"}}")));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class,
            e -> assertThat(e).hasMessage("OpenAI quota used up; check billing"));
    openAi.verify(1, postRequestedFor(urlPathEqualTo(PATH)));
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
    openAi.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(okJson("{\"status\": \"completed\", \"output\": []}")));

    assertThatThrownBy(() -> client().complete("s", "q"))
        .isInstanceOfSatisfying(
            OpenAiException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
  }
}
