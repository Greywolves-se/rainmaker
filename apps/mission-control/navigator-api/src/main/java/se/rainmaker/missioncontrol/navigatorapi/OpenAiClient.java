package se.rainmaker.missioncontrol.navigatorapi;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import se.rainmaker.missioncontrol.navigatorapi.OpenAiException.Kind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Asks OpenAI's chat completions API for one answer (ADR 0003).
 *
 * <p>A server error or a lost connection is retried once. A timeout is not retried, so the call
 * stays within the Lambda's own timeout; other errors (401, 429 …) are not retried either. Errors
 * become an {@link OpenAiException} with a safe message: never the key, the question or the body of
 * OpenAI's response.
 */
public final class OpenAiClient {

  private static final String CHAT_COMPLETIONS = "/v1/chat/completions";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final OpenAiSettings settings;
  private final String apiKey;
  private final HttpClient http;
  private final Retry retry;

  /**
   * Creates a client.
   *
   * @param apiKey the OpenAI key from Secrets Manager; never logged
   */
  public OpenAiClient(OpenAiSettings settings, String apiKey) {
    this.settings = Objects.requireNonNull(settings);
    this.apiKey = Objects.requireNonNull(apiKey);
    this.http = HttpClient.newBuilder().connectTimeout(settings.timeout()).build();
    this.retry =
        Retry.of(
            "openai",
            RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(settings.retryBackoff())
                .retryOnException(RetryableFailure.class::isInstance)
                .build());
  }

  /**
   * Returns OpenAI's answer to one question.
   *
   * @throws OpenAiException when OpenAI is unreachable, too slow or answers with an error
   */
  public String complete(String systemPrompt, String userMessage) {
    String body = requestBody(systemPrompt, userMessage);
    try {
      return retry.executeSupplier(() -> callOnce(body));
    } catch (RetryableFailure failure) {
      throw new OpenAiException(Kind.UNAVAILABLE, failure.getMessage());
    }
  }

  private String requestBody(String systemPrompt, String userMessage) {
    ObjectNode request = JSON.createObjectNode();
    request.put("model", settings.model());
    request.put("max_completion_tokens", settings.maxCompletionTokens());
    request
        .putArray("messages")
        .add(message("system", systemPrompt))
        .add(message("user", userMessage));
    return JSON.writeValueAsString(request);
  }

  private static ObjectNode message(String role, String content) {
    return JSON.createObjectNode().put("role", role).put("content", content);
  }

  private String callOnce(String body) {
    HttpRequest request =
        HttpRequest.newBuilder(settings.baseUrl().resolve(CHAT_COMPLETIONS))
            .timeout(settings.timeout())
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException timeout) {
      throw new OpenAiException(
          Kind.TIMEOUT, "OpenAI did not answer within " + settings.timeout().toMillis() + " ms");
    } catch (IOException connectionFailed) {
      throw new RetryableFailure("OpenAI could not be reached");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new OpenAiException(Kind.UNAVAILABLE, "Interrupted while waiting for OpenAI");
    }
    int status = response.statusCode();
    if (status >= 500) {
      throw new RetryableFailure("OpenAI answered HTTP " + status);
    }
    if (status != 200) {
      throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI answered HTTP " + status);
    }
    return answerText(response.body());
  }

  private static String answerText(String body) {
    try {
      JsonNode content =
          JSON.readTree(body).path("choices").path(0).path("message").path("content");
      if (!content.isString() || content.asString().isBlank()) {
        throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI's answer had no text");
      }
      return content.asString();
    } catch (JacksonException unreadable) {
      throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI's answer could not be read");
    }
  }

  /** A failure worth one more try: a server error or a lost connection. */
  private static final class RetryableFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    RetryableFailure(String message) {
      super(message);
    }
  }
}
