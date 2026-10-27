package se.rainmaker.publishercontrol.articles;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import se.rainmaker.publishercontrol.articles.ArticleChanges.ChangedArticle;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads G2's articles API (#150, contract 7.1): the changed articles ({@code ?since=…}) for the
 * sync, and one article's current state ({@code ?url=…}) for the approved links API. Both with a
 * connect and a read timeout and retries with back-off (CLAUDE.md, "External calls"). Only a
 * problem that may go away is retried: no connection, a timeout or a 5xx. A 4xx or an unusable
 * answer is not.
 */
@Component
public class G2ArticlesClient {

  private static final String ARTICLES_PATH = "/api/articles";
  private static final String CORRELATION_HEADER = "X-Correlation-Id";

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
    this.articlesUrl = properties.baseUrl().resolve(ARTICLES_PATH).toString();
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
    return parse(fetch("?since={since}", since.toString(), null), ArticleChanges.class);
  }

  /**
   * The article with this address as G2 has it now, or empty if G2 doesn't know it.
   *
   * @param correlationId sent on as {@code X-Correlation-Id} (contract 7), or {@code null}
   * @throws ArticlesUnavailableException when G2 can't deliver after the retries
   */
  public Optional<CurrentArticle> current(String articleUrl, String correlationId) {
    ArticleLookup answer =
        parse(fetch("?url={url}", articleUrl, correlationId), ArticleLookup.class);
    return answer.articles().stream().findFirst().map(G2ArticlesClient::current);
  }

  private static CurrentArticle current(ChangedArticle article) {
    return new CurrentArticle(
        article.articleId(), article.version(), article.url(), article.isGreen());
  }

  private String fetch(String query, String value, String correlationId) {
    try {
      return retry.executeSupplier(() -> fetchOnce(query, value, correlationId));
    } catch (RestClientException exception) {
      throw new ArticlesUnavailableException(
          "G2's articles API failed: " + exception.getMessage(), exception);
    }
  }

  /**
   * The answer's text. Read whole, then parsed, as in matching's G3 client: parsing while the JDK
   * client's timed body stream is read failed with "Stream closed".
   */
  private String fetchOnce(String query, String value, String correlationId) {
    return http.get()
        .uri(articlesUrl + query, value)
        .headers(
            headers -> {
              if (correlationId != null) {
                headers.set(CORRELATION_HEADER, correlationId);
              }
            })
        .retrieve()
        .body(String.class);
  }

  /** Not retried: asking again won't fix an answer that isn't the expected JSON. */
  private static <T> T parse(String body, Class<T> type) {
    if (body == null || body.isBlank()) {
      throw new ArticlesUnavailableException("G2's articles API answered with an empty body");
    }
    try {
      return JSON.readValue(body, type);
    } catch (JacksonException exception) {
      throw new ArticlesUnavailableException(
          "G2's articles API answered with an unusable article list: "
              + exception.getOriginalMessage(),
          exception);
    }
  }

  /** The answer to {@code ?url=…}: that one article, or an empty list (contract 7.1). */
  record ArticleLookup(List<ChangedArticle> articles) {

    ArticleLookup {
      if (articles == null) {
        throw new IllegalArgumentException("missing or invalid articles");
      }
    }
  }
}
