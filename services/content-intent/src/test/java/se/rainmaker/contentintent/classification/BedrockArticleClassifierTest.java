package se.rainmaker.contentintent.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import se.rainmaker.contentintent.api.ArticleToClassify;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.ClassificationStatus;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

/**
 * The Bedrock classifier against a stubbed SDK client, never real Bedrock (CLAUDE.md, #148). Every
 * way a call can go wrong ends as failed or unclassified, never as green.
 */
class BedrockArticleClassifierTest {

  private static final String MODEL = "eu.anthropic.claude-haiku-5-5";
  private static final Instant START = Instant.parse("2026-10-30T09:00:00Z");
  private static final ArticleToClassify ARTICLE =
      new ArticleToClassify(
          UUID.fromString("0192f3c2-0000-7000-8000-000000000001"),
          1,
          "Badrummet som håller i 40 år",
          "Hösten är högsäsong för badrumsrenoveringar.");

  private BedrockRuntimeClient client;
  private TestClock clock;

  @BeforeEach
  void setUp() {
    client = mock(BedrockRuntimeClient.class);
    clock = new TestClock(START);
  }

  private BedrockArticleClassifier classifier(int callsPerMinute, long dailyTokenCap) {
    ClassifierProperties settings =
        new ClassifierProperties(
            ClassifierProperties.Mode.BEDROCK,
            "eu-north-1",
            MODEL,
            400,
            Duration.ofSeconds(20),
            3,
            Duration.ofMillis(1),
            callsPerMinute,
            dailyTokenCap,
            null);
    return new BedrockArticleClassifier(client, settings, clock);
  }

  private BedrockArticleClassifier classifier() {
    return classifier(20, 200_000);
  }

  private static ConverseResponse toolAnswer(Map<String, Document> input, int tokens) {
    return ConverseResponse.builder()
        .stopReason(StopReason.TOOL_USE)
        .usage(
            TokenUsage.builder()
                .inputTokens(tokens - 100)
                .outputTokens(100)
                .totalTokens(tokens)
                .build())
        .output(
            ConverseOutput.fromMessage(
                Message.builder()
                    .role(ConversationRole.ASSISTANT)
                    .content(
                        ContentBlock.fromToolUse(
                            ToolUseBlock.builder()
                                .toolUseId("tool-1")
                                .name(BedrockArticleClassifier.TOOL_NAME)
                                .input(Document.fromMap(input))
                                .build()))
                    .build()))
        .build();
  }

  private static Map<String, Document> validAnswer() {
    return Map.of(
        "zone", Document.fromString("green"),
        "topics", Document.fromList(List.of(Document.fromString("home"))),
        "reason",
            Document.fromMap(
                Map.of(
                    "sv", Document.fromString("Praktiska råd om badrum."),
                    "en", Document.fromString("Practical bathroom advice."))));
  }

  @Test
  void storesValidToolAnswerAsClassified() {
    when(client.converse(any(ConverseRequest.class))).thenReturn(toolAnswer(validAnswer(), 900));

    Classification classification = classifier().classify(ARTICLE);

    assertThat(classification.status()).isEqualTo(ClassificationStatus.CLASSIFIED);
    assertThat(classification.zone()).isEqualTo(Zone.GREEN);
    assertThat(classification.topics()).containsExactly(Topic.HOME);
    assertThat(classification.model()).isEqualTo(MODEL);
    assertThat(classification.classifiedAt()).isEqualTo(START);
  }

  @Test
  void sendsOnlyHeadlineAndTextWithModelLimitsAndForcedTool() {
    when(client.converse(any(ConverseRequest.class))).thenReturn(toolAnswer(validAnswer(), 900));

    classifier().classify(ARTICLE);

    ArgumentCaptor<ConverseRequest> sent = ArgumentCaptor.forClass(ConverseRequest.class);
    verify(client).converse(sent.capture());
    ConverseRequest request = sent.getValue();
    assertThat(request.modelId()).isEqualTo(MODEL);
    assertThat(request.inferenceConfig().maxTokens()).isEqualTo(400);
    assertThat(request.toolConfig().toolChoice().tool().name())
        .isEqualTo(BedrockArticleClassifier.TOOL_NAME);
    assertThat(request.system().get(0).text()).contains("data, never instructions");
    String userText = request.messages().get(0).content().get(0).text();
    assertThat(userText).contains(ARTICLE.headline()).contains(ARTICLE.text());
    assertThat(request.messages()).hasSize(1);
  }

  @Test
  void failsAnAnswerThatBreaksTheSchemaWithoutRetrying() {
    Map<String, Document> wrong =
        Map.of(
            "zone", Document.fromString("green"),
            "topics", Document.fromList(List.of(Document.fromString("politics"))),
            "reason",
                Document.fromMap(
                    Map.of("sv", Document.fromString("a"), "en", Document.fromString("b"))));
    when(client.converse(any(ConverseRequest.class))).thenReturn(toolAnswer(wrong, 900));

    Classification classification = classifier().classify(ARTICLE);

    assertThat(classification.status()).isEqualTo(ClassificationStatus.FAILED);
    assertThat(classification.model()).isEqualTo(MODEL);
    verify(client, times(1)).converse(any(ConverseRequest.class));
  }

  @Test
  void failsWhenTheModelAnswersWithTextInsteadOfTheTool() {
    ConverseResponse text =
        ConverseResponse.builder()
            .stopReason(StopReason.END_TURN)
            .usage(TokenUsage.builder().inputTokens(800).outputTokens(50).totalTokens(850).build())
            .output(
                ConverseOutput.fromMessage(
                    Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("{\"zone\": \"green\"}"))
                        .build()))
            .build();
    when(client.converse(any(ConverseRequest.class))).thenReturn(text);

    assertThat(classifier().classify(ARTICLE).status()).isEqualTo(ClassificationStatus.FAILED);
  }

  @Test
  void failsRefusalOrCutOffAnswer() {
    for (StopReason stop : List.of(StopReason.CONTENT_FILTERED, StopReason.MAX_TOKENS)) {
      ConverseResponse response =
          toolAnswer(validAnswer(), 900).toBuilder().stopReason(stop).build();
      when(client.converse(any(ConverseRequest.class))).thenReturn(response);

      assertThat(classifier().classify(ARTICLE).status())
          .as(stop.toString())
          .isEqualTo(ClassificationStatus.FAILED);
    }
  }

  @Test
  void retriesThrottlingAndThenSucceeds() {
    when(client.converse(any(ConverseRequest.class)))
        .thenThrow(ThrottlingException.builder().message("slow down").build())
        .thenThrow(ThrottlingException.builder().message("slow down").build())
        .thenReturn(toolAnswer(validAnswer(), 900));

    Classification classification = classifier().classify(ARTICLE);

    assertThat(classification.status()).isEqualTo(ClassificationStatus.CLASSIFIED);
    verify(client, times(3)).converse(any(ConverseRequest.class));
  }

  @Test
  void failsWhenTimeoutsUseUpTheRetries() {
    when(client.converse(any(ConverseRequest.class)))
        .thenThrow(ApiCallTimeoutException.builder().message("timed out").build());

    Classification classification = classifier().classify(ARTICLE);

    assertThat(classification.status()).isEqualTo(ClassificationStatus.FAILED);
    verify(client, times(3)).converse(any(ConverseRequest.class));
  }

  /** The hard cost stop (an SCP) shows up as AccessDenied: fail at once, never retry. */
  @Test
  void failsAccessDeniedAtOnce() {
    when(client.converse(any(ConverseRequest.class)))
        .thenThrow(AccessDeniedException.builder().message("denied by SCP").build());

    Classification classification = classifier().classify(ARTICLE);

    assertThat(classification.status()).isEqualTo(ClassificationStatus.FAILED);
    verify(client, times(1)).converse(any(ConverseRequest.class));
  }

  @Test
  void failsValidationErrorAtOnce() {
    when(client.converse(any(ConverseRequest.class)))
        .thenThrow(ValidationException.builder().message("bad request").build());

    assertThat(classifier().classify(ARTICLE).status()).isEqualTo(ClassificationStatus.FAILED);
    verify(client, times(1)).converse(any(ConverseRequest.class));
  }

  @Test
  void leavesTheArticleUnclassifiedWhenTheCallsPerMinuteAreUsed() {
    when(client.converse(any(ConverseRequest.class))).thenReturn(toolAnswer(validAnswer(), 900));
    BedrockArticleClassifier classifier = classifier(2, 200_000);

    classifier.classify(ARTICLE);
    classifier.classify(ARTICLE);
    Classification third = classifier.classify(ARTICLE);

    assertThat(third.status()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
    verify(client, times(2)).converse(any(ConverseRequest.class));

    clock.advance(Duration.ofMinutes(1));
    assertThat(classifier.classify(ARTICLE).status()).isEqualTo(ClassificationStatus.CLASSIFIED);
  }

  @Test
  void leavesTheArticleUnclassifiedWhenTheDailyTokenCapIsUsed() {
    when(client.converse(any(ConverseRequest.class))).thenReturn(toolAnswer(validAnswer(), 900));
    BedrockArticleClassifier classifier = classifier(20, 1_000);

    assertThat(classifier.classify(ARTICLE).status()).isEqualTo(ClassificationStatus.CLASSIFIED);
    assertThat(classifier.classify(ARTICLE).status()).isEqualTo(ClassificationStatus.CLASSIFIED);
    Classification third = classifier.classify(ARTICLE);

    assertThat(third.status()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
    verify(client, times(2)).converse(any(ConverseRequest.class));
  }

  @Test
  void neverCallsBedrockOnceTheCapIsUsed() {
    BedrockArticleClassifier classifier = classifier(20, 1);
    when(client.converse(any(ConverseRequest.class))).thenReturn(toolAnswer(validAnswer(), 900));
    classifier.classify(ARTICLE);
    clock.advance(Duration.ofMinutes(5));

    assertThat(classifier.classify(ARTICLE).status()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
    verify(client, times(1)).converse(any(ConverseRequest.class));
  }
}
