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
 * Asks OpenAI's Responses API for one answer, as strict JSON {@code {"answer": "..."}} (ADR 0003,
 * #136). The request and answer handling follow the collector's client (#115), so both modules call
 * OpenAI the same way.
 *
 * <p>The retry rules are this module's own, because a reader is waiting: a server error or a lost
 * connection is retried once; a timeout, a rate limit (429) and other errors are not. Errors become
 * an {@link OpenAiException} with a safe message: never the key, the question or the body of
 * OpenAI's response.
 */
public final class OpenAiClient implements AnswerSource {

  private static final String RESPONSES = "/v1/responses";
  private static final String SCHEMA_NAME = "navigator_answer";
  private static final String NOT_AGREED_JSON =
      "OpenAI answered with text that is not the agreed JSON";
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
   * @throws OpenAiException when OpenAI is unreachable, too slow, refuses or answers with an error
   */
  @Override
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
    request.put("instructions", systemPrompt);
    request.put("input", userMessage);
    request.put("max_output_tokens", settings.maxOutputTokens());
    ObjectNode format = request.putObject("text").putObject("format");
    format.put("type", "json_schema");
    format.put("name", SCHEMA_NAME);
    format.put("strict", true);
    format.set("schema", answerSchema());
    return JSON.writeValueAsString(request);
  }

  /** The JSON the model must answer with: one string, {@code answer}. */
  private static ObjectNode answerSchema() {
    ObjectNode schema = JSON.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putObject("properties").putObject("answer").put("type", "string");
    schema.putArray("required").add("answer");
    return schema;
  }

  private String callOnce(String body) {
    HttpRequest request =
        HttpRequest.newBuilder(settings.baseUrl().resolve(RESPONSES))
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
    if (status == 429 && "insufficient_quota".equals(errorCode(response.body()))) {
      throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI quota used up; check billing");
    }
    if (status != 200) {
      throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI answered HTTP " + status);
    }
    return answer(readJson(response.body(), "OpenAI answered with a body that is not JSON"));
  }

  /** OpenAI's {@code error.code}; only the code is read, never the message (it can quote input). */
  private static String errorCode(String body) {
    try {
      return JSON.readTree(body).path("error").path("code").asString("");
    } catch (JacksonException unreadable) {
      return "";
    }
  }

  /** The {@code answer} inside the message's {@code output_text}; a refusal or early stop fails. */
  private static String answer(JsonNode response) {
    if ("incomplete".equals(response.path("status").asString(""))) {
      throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI stopped before the answer was complete");
    }
    for (JsonNode item : response.path("output")) {
      for (JsonNode content : item.path("content")) {
        String type = content.path("type").asString("");
        if ("refusal".equals(type)) {
          throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI refused to answer");
        }
        if ("output_text".equals(type)) {
          JsonNode answer =
              readJson(content.path("text").asString(""), NOT_AGREED_JSON).path("answer");
          if (!answer.isString() || answer.asString().isBlank()) {
            throw new OpenAiException(Kind.UNAVAILABLE, NOT_AGREED_JSON);
          }
          return answer.asString();
        }
      }
    }
    throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI's answer had no text");
  }

  private static JsonNode readJson(String text, String problem) {
    try {
      return JSON.readTree(text);
    } catch (JacksonException unreadable) {
      throw new OpenAiException(Kind.UNAVAILABLE, problem);
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
