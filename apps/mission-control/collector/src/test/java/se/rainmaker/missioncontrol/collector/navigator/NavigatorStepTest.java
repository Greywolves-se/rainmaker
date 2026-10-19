package se.rainmaker.missioncontrol.collector.navigator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;
import se.rainmaker.missioncontrol.collector.snapshot.Navigator;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class NavigatorStepTest {

  private static final String KEY = "sk-test-openai-key";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** Everyone in the contract sample; none of them may reach OpenAI. */
  private static final List<String> PEOPLE = List.of("Alex Berg", "Kim Ek", "Sam Lind");

  @RegisterExtension
  static WireMockExtension openAi =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void switchedOffStepAddsNoTextAndMakesNoCalls() {
    Snapshot result = step(false, KEY).addTo(sample());

    assertThat(result.navigator()).isNull();
    assertThat(result.sources().getLast())
        .extracting(Source::id, Source::status)
        .containsExactly("navigator-ai", Source.Status.DISABLED);
    assertThat(openAi.getAllServeEvents()).isEmpty();
  }

  @Test
  void withoutKeyTheStepIsAnErrorAndTheSnapshotIsKept() {
    Snapshot result = step(true, " ").addTo(sample());

    assertThat(result.navigator()).isNull();
    assertThat(result.sources().getLast())
        .extracting(Source::status, Source::message)
        .containsExactly(Source.Status.ERROR, "No OpenAI key: set OPENAI_API_KEY");
    assertThat(result.workItems()).isEqualTo(sample().workItems());
    assertThat(openAi.getAllServeEvents()).isEmpty();
  }

  @Test
  void writesTextForEveryPageInBothLanguagesWithNamesRestored() {
    stubAnswer(
        answer(
            "Person B must answer Q-7.",
            List.of("Q-7 is overdue."),
            "Person B behöver svara på Q-7."));

    Snapshot result = step(true, KEY).addTo(sample());

    Navigator navigator = result.navigator();
    assertThat(navigator.model()).isEqualTo("gpt-6-luna");
    assertThat(navigator.pages().overview().en().summary()).isEqualTo("Kim Ek must answer Q-7.");
    assertThat(navigator.pages().overview().sv().summary())
        .isEqualTo("Kim Ek behöver svara på Q-7.");
    assertThat(navigator.pages().development()).isNotNull();
    assertThat(navigator.pages().sources()).isNotNull();
    assertThat(result.sources().getLast().status()).isEqualTo(Source.Status.OK);
    assertThat(new SnapshotValidator().problems(result)).isEmpty();
    openAi.verify(3, postRequestedFor(urlPathEqualTo("/v1/responses")));
  }

  /** The acceptance criterion of #115: nothing that leaves names a person. */
  @Test
  void requestsContainLabelsButNoNames() {
    stubAnswer(answer("Fine.", List.of(), "Bra."));

    step(true, KEY).addTo(sample());

    List<LoggedRequest> requests =
        openAi.findAll(postRequestedFor(urlPathEqualTo("/v1/responses")));
    assertThat(requests).hasSize(3);
    for (LoggedRequest request : requests) {
      String body = request.getBodyAsString();
      for (String person : PEOPLE) {
        assertThat(body).doesNotContainIgnoringCase(person);
      }
      assertThat(body).doesNotContain("https://");
    }
    assertThat(requests)
        .anySatisfy(request -> assertThat(request.getBodyAsString()).contains("Person B"));
  }

  @Test
  void requestAsksForStructuredJsonWithTheConfiguredModel() {
    stubAnswer(answer("Fine.", List.of(), "Bra."));

    step(true, KEY).addTo(sample());

    openAi.verify(
        3,
        postRequestedFor(urlPathEqualTo("/v1/responses"))
            .withHeader("Authorization", equalTo("Bearer " + KEY))
            .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-6-luna")))
            .withRequestBody(matchingJsonPath("$.max_output_tokens", equalTo("1200")))
            .withRequestBody(matchingJsonPath("$.text.format.type", equalTo("json_schema")))
            .withRequestBody(matchingJsonPath("$.text.format.strict", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.instructions", containing("not instructions"))));
  }

  @Test
  void onePageFailingLeavesTheOthers(CapturedOutput output) {
    stubAnswer(answer("Fine.", List.of(), "Bra."));
    openAi.stubFor(
        post(urlPathEqualTo("/v1/responses"))
            .withRequestBody(containing("Page: development"))
            .atPriority(1)
            .willReturn(okJson(response("not the agreed json"))));

    Snapshot result = step(true, KEY).addTo(sample());

    assertThat(result.navigator().pages().development()).isNull();
    assertThat(result.navigator().pages().overview()).isNotNull();
    assertThat(result.navigator().pages().sources()).isNotNull();
    assertThat(result.sources().getLast())
        .extracting(Source::status, Source::message)
        .containsExactly(Source.Status.OK, "No AI text for: development");
    assertThat(new SnapshotValidator().problems(result)).isEmpty();
  }

  /** A failing server is retried, then the remaining pages are given the same message. */
  @Test
  void failingServerStopsTheRestOfTheRun() {
    openAi.stubFor(post(urlPathEqualTo("/v1/responses")).willReturn(aResponse().withStatus(500)));

    Snapshot result = step(true, KEY).addTo(sample());

    assertThat(result.navigator()).isNull();
    assertThat(result.sources().getLast().message())
        .isEqualTo("OpenAI did not answer after 2 attempts (last: HTTP 500)");
    openAi.verify(2, postRequestedFor(urlPathEqualTo("/v1/responses")));
  }

  @Test
  void rejectedKeyGivesSafeErrorAndNoText(CapturedOutput output) {
    openAi.stubFor(
        post(urlPathEqualTo("/v1/responses"))
            .willReturn(aResponse().withStatus(401).withBody("{\"error\": \"key " + KEY + "\"}")));

    Snapshot result = step(true, KEY).addTo(sample());

    assertThat(result.navigator()).isNull();
    assertThat(result.sources().getLast())
        .extracting(Source::status, Source::message)
        .containsExactly(
            Source.Status.ERROR, "OpenAI rejected the API key (HTTP 401); check OPENAI_API_KEY");
    assertThat(output.getAll()).doesNotContain(KEY);
    // A rejected key is not retried, and the other pages are not tried: it would fail them too.
    openAi.verify(1, postRequestedFor(urlPathEqualTo("/v1/responses")));
  }

  @Test
  void busyServiceIsRetriedThenTheRunStops() {
    openAi.stubFor(
        post(urlPathEqualTo("/v1/responses"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withBody("{\"error\": {\"code\": \"rate_limit_exceeded\"}}")));

    Snapshot result = step(true, KEY).addTo(sample());

    assertThat(result.sources().getLast().message())
        .isEqualTo("OpenAI did not answer after 2 attempts (last: rate limit, HTTP 429)");
    // The first page uses both attempts; the others are not tried in a run that already failed.
    openAi.verify(2, postRequestedFor(urlPathEqualTo("/v1/responses")));
  }

  /** A new account without billing answers 429 with insufficient_quota: not worth retrying. */
  @Test
  void usedUpQuotaIsReportedAtOnceWithoutRetry() {
    openAi.stubFor(
        post(urlPathEqualTo("/v1/responses"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withBody(
                        "{\"error\": {\"code\": \"insufficient_quota\","
                            + " \"message\": \"secret\"}}")));

    Snapshot result = step(true, KEY).addTo(sample());

    assertThat(result.sources().getLast())
        .extracting(Source::status, Source::message)
        .containsExactly(Source.Status.ERROR, "OpenAI quota used up; check billing");
    openAi.verify(1, postRequestedFor(urlPathEqualTo("/v1/responses")));
  }

  /** One step serves every project in a run: after a run-wide failure the next project skips. */
  @Test
  void failureForWholeRunAlsoSkipsTheNextProject() {
    openAi.stubFor(post(urlPathEqualTo("/v1/responses")).willReturn(aResponse().withStatus(401)));
    NavigatorStep step = step(true, KEY);

    step.addTo(sample());
    Snapshot second = step.addTo(sample());

    assertThat(second.sources().getLast().message())
        .isEqualTo("OpenAI rejected the API key (HTTP 401); check OPENAI_API_KEY");
    openAi.verify(1, postRequestedFor(urlPathEqualTo("/v1/responses")));
  }

  /** Jackson 3 throws an unchecked exception on a broken body; only the AI text may be lost. */
  @Test
  void bodyThatIsNotJsonOnSuccessLeavesTheSnapshotIntact() {
    openAi.stubFor(post(urlPathEqualTo("/v1/responses")).willReturn(okJson("{not json")));

    Snapshot result = step(true, KEY).addTo(sample());

    assertThat(result.navigator()).isNull();
    assertThat(result.sources().getLast())
        .extracting(Source::status, Source::message)
        .containsExactly(Source.Status.ERROR, "OpenAI answered with a body that is not JSON");
    assertThat(result.workItems()).isEqualTo(sample().workItems());
  }

  @Test
  void refusalLeavesThePageEmpty() {
    openAi.stubFor(
        post(urlPathEqualTo("/v1/responses"))
            .willReturn(
                okJson(
                    "{\"status\": \"completed\", \"output\": [{\"type\": \"message\","
                        + " \"content\": [{\"type\": \"refusal\", \"refusal\": \"No.\"}]}]}")));

    Snapshot result = step(true, KEY).addTo(sample());

    assertThat(result.navigator()).isNull();
    assertThat(result.sources().getLast().message()).isEqualTo("OpenAI refused to answer");
  }

  @Test
  void answerThatIsNotTheAgreedJsonLeavesThePageEmpty() {
    openAi.stubFor(
        post(urlPathEqualTo("/v1/responses")).willReturn(okJson(response("not json at all"))));

    Snapshot result = step(true, KEY).addTo(sample());

    assertThat(result.navigator()).isNull();
    assertThat(result.sources().getLast().message())
        .isEqualTo("OpenAI answered with text that is not the agreed JSON");
  }

  @Test
  void tooLongOrTooManyItemsAreCutToTheContractLimits() {
    String longText = "x".repeat(700);
    List<String> seven = List.of("1", "2", "3", "4", "5", "6", "7");
    stubAnswer(answer(longText, seven, "Kort."));

    Snapshot result = step(true, KEY).addTo(sample());

    Navigator.Insight english = result.navigator().pages().overview().en();
    assertThat(english.summary()).hasSize(600).endsWith("…");
    assertThat(english.deviations()).hasSize(5);
    assertThat(new SnapshotValidator().problems(result)).isEmpty();
  }

  /** Cutting must never split an emoji (two UTF-16 units) and leave half of it behind. */
  @Test
  void cuttingNeverSplitsAnEmoji() {
    String text = "x".repeat(598) + "😀" + "y".repeat(10);
    stubAnswer(answer(text, List.of(), "Kort."));

    Snapshot result = step(true, KEY).addTo(sample());

    String summary = result.navigator().pages().overview().en().summary();
    assertThat(summary).endsWith("x…");
    // No half of an emoji is left: a lone half shows up as a code point in the surrogate range.
    assertThat(
            summary
                .codePoints()
                .noneMatch(
                    point -> point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE))
        .isTrue();
    assertThat(summary.length()).isLessThanOrEqualTo(600);
    assertThat(new SnapshotValidator().problems(result)).isEmpty();
  }

  private static NavigatorStep step(boolean enabled, String key) {
    NavigatorProperties properties =
        new NavigatorProperties(
            enabled,
            URI.create(openAi.baseUrl() + "/v1"),
            key,
            "gpt-6-luna",
            1200,
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            2,
            Duration.ofMillis(1));
    return new NavigatorStep(
        new OpenAiClient(properties),
        properties,
        Clock.fixed(Instant.parse("2026-11-02T06:00:30Z"), ZoneOffset.UTC));
  }

  private static Snapshot sample() {
    return SnapshotJson.mapper().readValue(ContractFiles.sampleJson(), Snapshot.class);
  }

  private static void stubAnswer(String outputText) {
    openAi.stubFor(post(urlPathEqualTo("/v1/responses")).willReturn(okJson(response(outputText))));
  }

  /** A Responses API answer whose output text is {@code outputText}. */
  private static String response(String outputText) {
    return JSON.writeValueAsString(
        Map.of(
            "status",
            "completed",
            "output",
            List.of(
                Map.of(
                    "type",
                    "message",
                    "content",
                    List.of(Map.of("type", "output_text", "text", outputText))))));
  }

  /** The model's JSON: the same text in English, and {@code swedish} as the Swedish summary. */
  private static String answer(String english, List<String> deviations, String swedish) {
    Map<String, Object> en =
        Map.of("summary", english, "deviations", deviations, "suggestions", List.of());
    Map<String, Object> sv =
        Map.of("summary", swedish, "deviations", deviations, "suggestions", List.of());
    return JSON.writeValueAsString(Map.of("sv", sv, "en", en));
  }
}
