package se.rainmaker.publishercontrol.articles;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.time.Instant;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the changed articles from G2's API, {@code GET /api/articles?since=…} (#150, contract 7.1),
 * with a connect and a read timeout and retries with back-off (CLAUDE.md, "External calls"). Only a
 * problem that may go away is retried: no connection, a timeout or a 5xx. A 4xx or an unusable
 * answer is not.
 */
@Component
class G2ArticlesClient {

  private static final String ARTICLES_PATH = "/api/articles";

  /** G2 sends more fields than G3 needs; those are ignored, never an error. */
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final RestClient http;
  private final String articlesUrl;
  private final Retry retry;

  /** Builds the HTTP client and the retry policy from the settings. */
  G2ArticlesClient(ArticlesProperties properties) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    requestFactory.setReadTimeout(properties.readTimeout());
    this.http = RestClient.builder().requestFactory(requestFactory).build();
    this.articlesUrl = properties.baseUrl().resolve(ARTICLES_PATH) + "?since={since}";
    this.retry =
        Retry.of(
            "g2-articles",
            RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.backoff(), 2.0))
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
  }

  /**
   * Every article that changed at or after {@code since}.
   *
   * @throws ArticlesUnavailableException when G2 can't deliver after the retries
   */
  ArticleChanges changedSince(Instant since) {
    String body;
    try {
      body = retry.executeSupplier(() -> fetchOnce(since));
    } catch (RestClientException exception) {
      throw new ArticlesUnavailableException(
          "G2's articles API failed: " + exception.getMessage(), exception);
    }
    return parse(body);
  }

  /**
   * The answer's text. Read whole, then parsed, as in matching's G3 client: parsing while the JDK
   * client's timed body stream is read failed with "Stream closed".
   */
  private String fetchOnce(Instant since) {
    return http.get().uri(articlesUrl, since.toString()).retrieve().body(String.class);
  }

  /** Not retried: asking again won't fix an answer that isn't the expected JSON. */
  private static ArticleChanges parse(String body) {
    if (body == null || body.isBlank()) {
      throw new ArticlesUnavailableException("G2's articles API answered with an empty body");
    }
    try {
      return JSON.readValue(body, ArticleChanges.class);
    } catch (JacksonException exception) {
      throw new ArticlesUnavailableException(
          "G2's articles API answered with an unusable article list: "
              + exception.getOriginalMessage(),
          exception);
    }
  }
}
