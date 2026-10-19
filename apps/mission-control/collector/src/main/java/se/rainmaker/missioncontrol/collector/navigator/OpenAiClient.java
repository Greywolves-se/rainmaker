package se.rainmaker.missioncontrol.collector.navigator;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calls the OpenAI Responses API ({@code POST /responses}) with a JSON Schema for the answer
 * (structured output, {@code strict}), and returns the answer text (#115, ADR 0002). Timeouts and
 * server errors are retried with back-off; every other failure becomes a {@link NavigatorException}
 * with a message that is safe to show. The API key is never logged and is only sent to {@code
 * apiUrl}.
 */
@Component
public class OpenAiClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final NavigatorProperties properties;
  private final RestClient http;
  private final Retry retry;

  /** Builds the HTTP client with the timeouts and retry policy from the settings. */
  public OpenAiClient(NavigatorProperties properties) {
    this.properties = properties;
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    requestFactory.setReadTimeout(properties.readTimeout());
    this.http =
        RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.USER_AGENT, "rainmaker-mission-control")
            .build();
    this.retry =
        Retry.of(
            "openai",
            RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .retryExceptions(RetryableOpenAiException.class)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.backoff(), 2.0))
                .build());
  }

  /**
   * The model's answer text for {@code input}, following {@code schema}.
   *
   * @throws NavigatorException when OpenAI refuses, stops early or cannot deliver
   */
  public String respond(String instructions, String input, String schemaName, ObjectNode schema) {
    ObjectNode body = JSON.createObjectNode();
    body.put("model", properties.model());
    body.put("instructions", instructions);
    body.put("input", input);
    body.put("max_output_tokens", properties.maxOutputTokens());
    ObjectNode format = body.putObject("text").putObject("format");
    format.put("type", "json_schema");
    format.put("name", schemaName);
    format.put("strict", true);
    format.set("schema", schema);
    URI uri = URI.create(properties.apiUrl() + "/responses");
    JsonNode answer;
    try {
      answer = retry.executeSupplier(() -> post(uri, body));
    } catch (RetryableOpenAiException e) {
      // Retries ran out: the next calls would wait just as long, so the run stops trying.
      throw NavigatorException.forWholeRun(
          "OpenAI did not answer after "
              + properties.maxAttempts()
              + " attempts (last: "
              + e.getMessage()
              + ")");
    }
    return outputText(answer);
  }

  private JsonNode post(URI uri, ObjectNode body) {
    try {
      return http.post()
          .uri(uri)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(headers -> headers.setBearerAuth(properties.apiKey()))
          .body(JSON.writeValueAsString(body))
          .exchange((request, response) -> toJson(response));
    } catch (ResourceAccessException e) {
      log.atWarn().log("OpenAI request timed out or failed to connect");
      throw new RetryableOpenAiException("no answer in time");
    }
  }

  private static JsonNode toJson(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) throws IOException {
    int status = response.getStatusCode().value();
    if (status == 200) {
      try {
        return JSON.readTree(response.getBody());
      } catch (JacksonException e) {
        // Jackson 3's exception is unchecked: without this it would fail the whole project.
        throw new NavigatorException("OpenAI answered with a body that is not JSON");
      }
    }
    if (status == 401) {
      throw NavigatorException.forWholeRun(
          "OpenAI rejected the API key (HTTP 401); check OPENAI_API_KEY");
    }
    if (status == 429) {
      // Only error.code is read, never the message: it can quote the request.
      if ("insufficient_quota".equals(errorCode(response))) {
        throw NavigatorException.forWholeRun("OpenAI quota used up; check billing");
      }
      log.atWarn().addKeyValue("status", status).log("OpenAI rate limit");
      throw new RetryableOpenAiException("rate limit, HTTP 429");
    }
    if (status >= 500) {
      log.atWarn().addKeyValue("status", status).log("OpenAI failing");
      throw new RetryableOpenAiException("HTTP " + status);
    }
    throw new NavigatorException("OpenAI answered with HTTP " + status);
  }

  /** OpenAI's {@code error.code}, e.g. {@code insufficient_quota}; empty when there is none. */
  private static String errorCode(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
    try {
      return JSON.readTree(response.getBody()).path("error").path("code").asString("");
    } catch (IOException | JacksonException e) {
      // No readable code: treated as an ordinary rate limit.
      return "";
    }
  }

  /** The {@code output_text} of the answer's message; a refusal or early stop is an error. */
  private static String outputText(JsonNode answer) {
    if ("incomplete".equals(answer.path("status").asString(""))) {
      throw new NavigatorException("OpenAI stopped before the answer was complete");
    }
    for (JsonNode item : answer.path("output")) {
      for (JsonNode content : item.path("content")) {
        String type = content.path("type").asString("");
        if ("refusal".equals(type)) {
          throw new NavigatorException("OpenAI refused to answer");
        }
        if ("output_text".equals(type)) {
          return content.path("text").asString("");
        }
      }
    }
    throw new NavigatorException("OpenAI's answer had no text");
  }

  /** Parses the model's text as JSON; it must follow the schema it was given. */
  static JsonNode parse(String text) {
    try {
      JsonNode node = JSON.readTree(text);
      if (!node.isObject()) {
        throw new NavigatorException("OpenAI answered with text that is not the agreed JSON");
      }
      return node;
    } catch (JacksonException e) {
      throw new NavigatorException("OpenAI answered with text that is not the agreed JSON");
    }
  }

  /** A failure worth retrying: a timeout, a rate limit or a server error. */
  static final class RetryableOpenAiException extends RuntimeException {

    RetryableOpenAiException(String message) {
      super(message);
    }
  }
}
