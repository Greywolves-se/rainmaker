package se.rainmaker.missioncontrol.navigatorapi;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * No person name from the snapshot reaches OpenAI, neither in the page data nor in the question,
 * and the reader gets the real names back (ADR 0002, ADR 0003). OpenAI is WireMock, never the real
 * API.
 */
class AskNavigatorAnonymizationTest {

  @RegisterExtension
  static WireMockExtension openAi =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String PATH = "/v1/chat/completions";

  private static AskNavigator navigator(PageData pageData) {
    Clock clock = Clock.fixed(Instant.parse("2026-10-19T08:15:00Z"), ZoneOffset.UTC);
    OpenAiClient openAiClient =
        new OpenAiClient(
            new OpenAiSettings(
                URI.create(openAi.baseUrl()),
                "test-model",
                300,
                Duration.ofSeconds(5),
                Duration.ofMillis(1)),
            "sk-test-not-a-real-key");
    return new AskNavigator(
        new AskRequestValidator(Set.of("rainmaker")),
        new KillSwitch(() -> "true", clock),
        new QuestionLimits(new InMemoryQuestionCounter(), clock),
        (project, page) -> Optional.of(pageData),
        openAiClient,
        "test-model",
        clock);
  }

  private static String request(String question) {
    return "{\"project\": \"rainmaker\", \"page\": \"overview\", \"question\": \""
        + question
        + "\", \"language\": \"sv\"}";
  }

  private static void openAiAnswers(String content) {
    openAi.stubFor(
        post(urlPathEqualTo(PATH))
            .willReturn(
                okJson(
                    "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \""
                        + content
                        + "\"}}]}")));
  }

  @Test
  void sendsLabelsInsteadOfNamesAndRestoresTheNamesInTheAnswer() {
    openAiAnswers("Person A arbetar med #115.");
    PageData pageData =
        new PageData(
            "Work items: #115 assigned to Kim Ek (kim-ek), reviewed by Sam Lind.",
            List.of("Kim Ek", "kim-ek", "Sam Lind"));

    HttpReply reply = navigator(pageData).handle("sub-123", request("Vad jobbar Kim Ek med?"));

    openAi.verify(
        postRequestedFor(urlPathEqualTo(PATH))
            .withRequestBody(containing("Person A"))
            .withRequestBody(notContaining("Kim"))
            .withRequestBody(notContaining("kim-ek"))
            .withRequestBody(notContaining("Sam Lind")));
    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.body()).contains("Kim Ek arbetar med #115.").doesNotContain("Person A");
  }

  @Test
  void hidesNameThatIsOnlyInTheQuestionWhenThePersonIsInTheSnapshot() {
    openAiAnswers("Person A har inga blockerade uppgifter.");
    PageData pageData = new PageData("Work items: none blocked.", List.of("Kim"));

    HttpReply reply = navigator(pageData).handle("sub-123", request("Vad jobbar Kim med?"));

    openAi.verify(
        postRequestedFor(urlPathEqualTo(PATH))
            .withRequestBody(containing("Vad jobbar Person A med?"))
            .withRequestBody(notContaining("Kim")));
    assertThat(reply.body()).contains("Kim har inga blockerade uppgifter.");
  }
}
