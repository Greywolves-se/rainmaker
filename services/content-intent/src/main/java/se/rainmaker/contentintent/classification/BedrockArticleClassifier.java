package se.rainmaker.contentintent.classification;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.rainmaker.contentintent.api.ArticleClassifier;
import se.rainmaker.contentintent.api.ArticleToClassify;
import se.rainmaker.contentintent.api.Classification;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ModelNotReadyException;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceUnavailableException;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Classifies an article with Claude on Amazon Bedrock (#148, contract section 8).
 *
 * <p>How one article is classified:
 *
 * <ol>
 *   <li>Our own brakes first: the daily token cap and the calls-per-minute limit. If either says
 *       no, no call is made and the article stays {@code unclassified}, to be classified later.
 *   <li>One Converse call: the system prompt ({@code content-intent/classification-prompt.txt}),
 *       the headline and text as data (escaped, and the text cut to {@code maxTextChars}), a small
 *       {@code maxTokens}, temperature 0, and the contract's schema as a tool Claude must call.
 *       Nothing about readers is ever sent.
 *   <li>Temporary errors (throttling, timeouts, service errors) are retried with exponential
 *       back-off and jitter, at most {@code maxAttempts} times in all. Everything else fails at
 *       once and is logged as an error: {@code AccessDenied}, which is how the hard cost stop (an
 *       SCP) shows up, and client errors such as an expired SSO login.
 *   <li>The tool input must match the schema ({@link ClaudeAnswerParser}). A refusal, a cut-off
 *       answer, a text answer or an invalid one is {@code failed}: never green by default.
 * </ol>
 *
 * <p>The SDK client is built with its own retries switched off, so this class alone decides how
 * often Bedrock is called. Logs carry the article id, version, outcome and tokens, never the text.
 */
public class BedrockArticleClassifier implements ArticleClassifier {

  /** The tool Claude must call with its answer. */
  static final String TOOL_NAME = "record_classification";

  static final String PROMPT_RESOURCE = "content-intent/classification-prompt.txt";

  private static final Logger LOG = LoggerFactory.getLogger(BedrockArticleClassifier.class);

  private static final double BACKOFF_MULTIPLIER = 2.0;

  /** Each wait is randomly up to 50 % shorter or longer than the exponential value. */
  private static final double BACKOFF_JITTER = 0.5;

  private final BedrockRuntimeClient client;
  private final ClassifierProperties settings;
  private final Clock clock;
  private final CallRateLimiter rateLimiter;
  private final DailyTokenBudget tokenBudget;
  private final ClaudeAnswerParser parser = new ClaudeAnswerParser();
  private final Retry retry;
  private final String systemPrompt;
  private final ToolConfiguration toolConfig;

  /** Creates the classifier; the limits start empty. */
  public BedrockArticleClassifier(
      BedrockRuntimeClient client, ClassifierProperties settings, Clock clock) {
    this.client = client;
    this.settings = settings;
    this.clock = clock;
    this.rateLimiter = new CallRateLimiter(settings.callsPerMinute(), clock);
    this.tokenBudget = new DailyTokenBudget(settings.dailyTokenCap(), clock);
    this.retry =
        Retry.of(
            "bedrock-classify",
            RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                // Exponential with jitter, so calls throttled together don't retry together.
                .intervalFunction(
                    IntervalFunction.ofExponentialRandomBackoff(
                        settings.backoff(), BACKOFF_MULTIPLIER, BACKOFF_JITTER))
                .retryOnException(BedrockArticleClassifier::isTemporary)
                .build());
    this.systemPrompt = readPrompt();
    this.toolConfig =
        ToolConfiguration.builder()
            .tools(
                Tool.fromToolSpec(
                    ToolSpecification.builder()
                        .name(TOOL_NAME)
                        .description("Record the article's zone, topics and reason.")
                        .inputSchema(
                            ToolInputSchema.fromJson(
                                JsonDocuments.toDocument(parser.toolInputSchema())))
                        .build()))
            .toolChoice(ToolChoice.fromTool(SpecificToolChoice.builder().name(TOOL_NAME).build()))
            .build();
  }

  @Override
  public Classification classify(ArticleToClassify article) {
    if (!tokenBudget.hasRoom()) {
      log(article, "deferred-daily-token-cap", 0);
      return Classification.unclassified();
    }
    ConverseRequest request = request(article);
    ConverseResponse response;
    try {
      response = retry.executeSupplier(() -> callOnce(request));
    } catch (RateLimitedException e) {
      log(article, "deferred-calls-per-minute", 0);
      return Classification.unclassified();
    } catch (SdkException e) {
      logFailure(article, e);
      return Classification.failed(settings.modelId(), clock.instant());
    }
    long tokens = response.usage() == null ? 0 : response.usage().totalTokens();
    tokenBudget.spend(tokens);
    Optional<ClaudeAnswer> answer =
        response.stopReason() == StopReason.TOOL_USE ? toolInput(response) : Optional.empty();
    if (answer.isEmpty()) {
      log(article, "failed-invalid-answer-" + response.stopReasonAsString(), tokens);
      return Classification.failed(settings.modelId(), clock.instant());
    }
    log(article, "classified", tokens);
    return Classification.classified(
        answer.get().zone(),
        answer.get().topics(),
        answer.get().reason(),
        settings.modelId(),
        clock.instant());
  }

  /** One Bedrock call, if the calls-per-minute limit allows it. */
  private ConverseResponse callOnce(ConverseRequest request) {
    if (!rateLimiter.tryAcquire()) {
      throw new RateLimitedException();
    }
    return client.converse(request);
  }

  private ConverseRequest request(ArticleToClassify article) {
    String text = article.text();
    if (text.length() > settings.maxTextChars()) {
      text = text.substring(0, settings.maxTextChars());
    }
    // Escaped, so the article can't close these tags and pose as text outside the data.
    String articleAsData =
        "<article>\n<headline>"
            + escapeTags(article.headline())
            + "</headline>\n<text>"
            + escapeTags(text)
            + "</text>\n</article>";
    return ConverseRequest.builder()
        .modelId(settings.modelId())
        .system(SystemContentBlock.builder().text(systemPrompt).build())
        .messages(
            Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(articleAsData))
                .build())
        .inferenceConfig(
            InferenceConfiguration.builder()
                .maxTokens(settings.maxTokens())
                .temperature(0f)
                .build())
        .toolConfig(toolConfig)
        .build();
  }

  private Optional<ClaudeAnswer> toolInput(ConverseResponse response) {
    if (response.output() == null || response.output().message() == null) {
      return Optional.empty();
    }
    return response.output().message().content().stream()
        .map(ContentBlock::toolUse)
        .filter(toolUse -> toolUse != null && TOOL_NAME.equals(toolUse.name()))
        .map(ToolUseBlock::input)
        .findFirst()
        .flatMap(input -> parser.parse(JsonDocuments.toJson(input)));
  }

  private static String escapeTags(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Throttling, timeouts and service-side errors may pass; everything else will not. Other client
   * errors, such as an expired SSO login, are not retried: they won't pass on a second try.
   */
  private static boolean isTemporary(Throwable error) {
    return error instanceof ThrottlingException
        || error instanceof ServiceUnavailableException
        || error instanceof InternalServerException
        || error instanceof ModelNotReadyException
        || error instanceof ApiCallTimeoutException
        || error instanceof ApiCallAttemptTimeoutException;
  }

  /**
   * Access denied (also how the hard cost stop's SCP shows up) and client errors such as an expired
   * login are errors that need a person; anything else is a warning about one article.
   */
  private void logFailure(ArticleToClassify article, SdkException error) {
    String outcome;
    String message;
    boolean needsPerson = true;
    if (error instanceof AccessDeniedException) {
      outcome = "failed-access-denied";
      message = "Bedrock access denied: the cost stop (SCP) or the role's permissions";
    } else if (error instanceof SdkClientException && !isTemporary(error)) {
      outcome = "failed-client-error";
      message = "Bedrock call could not be made; check the AWS login and settings";
    } else {
      outcome = "failed-bedrock-error";
      message = "Bedrock call failed; classification stored as failed";
      needsPerson = false;
    }
    (needsPerson ? LOG.atError() : LOG.atWarn())
        .addKeyValue("article_id", article.articleId())
        .addKeyValue("article_version", article.version())
        .addKeyValue("outcome", outcome)
        .addKeyValue("error", error.getClass().getSimpleName())
        .log(message);
  }

  private void log(ArticleToClassify article, String outcome, long tokens) {
    LOG.atInfo()
        .addKeyValue("article_id", article.articleId())
        .addKeyValue("article_version", article.version())
        .addKeyValue("outcome", outcome)
        .addKeyValue("tokens", tokens)
        .log("Article classification");
  }

  private static String readPrompt() {
    try (InputStream in =
        BedrockArticleClassifier.class.getClassLoader().getResourceAsStream(PROMPT_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(PROMPT_RESOURCE + " is not on the classpath");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + PROMPT_RESOURCE, e);
    }
  }

  /** The calls-per-minute limit refused the call; never retried. */
  private static final class RateLimitedException extends RuntimeException {
    RateLimitedException() {
      super("Calls per minute used", null, false, false);
    }
  }
}
