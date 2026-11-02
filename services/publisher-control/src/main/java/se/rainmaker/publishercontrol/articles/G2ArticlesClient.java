package se.rainmaker.publishercontrol.articles;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import se.rainmaker.publishercontrol.articles.ArticleList.G2Article;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads G2's articles API (#150, contract 7.1): a snapshot of the recent articles ({@code
 * ?recent=…}) for the sync, and one article's current state ({@code ?url=…}) for the approved links
 * API. Both answer {@code {"articles": [...]}}, with a connect and a read timeout and retries with
 * back-off (CLAUDE.md, "External calls"). Only a problem that may go away is retried: no
 * connection, a timeout or a 5xx. A 4xx or an unusable answer is not.
 *
 * <p>The reader path's call ({@code current}, #230) runs on every request from G1, which waits only
 * {@link ArticlesProperties#G1_READ_TIMEOUT} for G3. So it has its own short timeouts, one attempt
 * and a circuit breaker: while G2 keeps failing, it isn't called at all and G3 answers {@code 503}
 * at once. The poll keeps the longer timeouts and retries, and isn't affected by the circuit.
 */
@Component
public class G2ArticlesClient {

  private static final String ARTICLES_PATH = "/api/articles";
  private static final String CORRELATION_HEADER = "X-Correlation-Id";

  private static final Logger LOG = LoggerFactory.getLogger(G2ArticlesClient.class);

  /** G2 sends more fields than G3 needs; those are ignored, never an error. */
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final RestClient http;
  private final RestClient readerHttp;
  private final CircuitBreaker readerCircuit;
  private final String articlesUrl;
  private final int recent;
  private final Retry retry;

  /** Builds the HTTP clients, the retry policy and the circuit breaker from the settings. */
  @Autowired
  G2ArticlesClient(ArticlesProperties properties) {
    this(properties, Clock.systemUTC());
  }

  /** With the clock the circuit breaker reads, so tests can move it. */
  G2ArticlesClient(ArticlesProperties properties, Clock clock) {
    this.http = restClient(properties.connectTimeout(), properties.readTimeout());
    this.readerHttp =
        restClient(properties.reader().connectTimeout(), properties.reader().readTimeout());
    this.readerCircuit = readerCircuit(properties.reader().circuitBreaker(), clock);
    this.articlesUrl = properties.baseUrl().resolve(ARTICLES_PATH).toString();
    this.recent = properties.recent();
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
   * The latest version of the {@code recent} articles G2 stored most recently, each with its
   * current classification and suggestions.
   *
   * @throws ArticlesUnavailableException when G2 can't deliver after the retries
   */
  ArticleList recent() {
    return parse(fetch("?recent={recent}", String.valueOf(recent), null));
  }

  /**
   * The article with this address as G2 has it now, or empty if G2 doesn't know it.
   *
   * @param correlationId sent on as {@code X-Correlation-Id} (contract 7), or {@code null}
   * @throws ArticlesUnavailableException when G2 can't deliver after the retries
   */
  public Optional<CurrentArticle> current(String articleUrl, String correlationId) {
    ArticleList answer = parse(fetchForReader(articleUrl, correlationId));
    return answer.articles().stream().findFirst().map(G2ArticlesClient::current);
  }

  private static CurrentArticle current(G2Article article) {
    return new CurrentArticle(
        article.articleId(), article.version(), article.url(), article.isGreen());
  }

  private String fetch(String query, String value, String correlationId) {
    try {
      return retry.executeSupplier(() -> fetchOnce(http, query, value, correlationId));
    } catch (RestClientException exception) {
      throw new ArticlesUnavailableException(
          "G2's articles API failed: " + exception.getMessage(), exception);
    }
  }

  /** One attempt, through the circuit breaker; never the poll's retries. */
  private String fetchForReader(String articleUrl, String correlationId) {
    try {
      return readerCircuit.executeSupplier(
          () -> fetchOnce(readerHttp, "?url={url}", articleUrl, correlationId));
    } catch (CallNotPermittedException open) {
      throw new ArticlesUnavailableException(
          "G2's articles API isn't called while the reader circuit is open", open);
    } catch (RestClientException exception) {
      throw new ArticlesUnavailableException(
          "G2's articles API failed: " + exception.getMessage(), exception);
    }
  }

  /**
   * The answer's text. Read whole, then parsed, as in matching's G3 client: parsing while the JDK
   * client's timed body stream is read failed with "Stream closed".
   */
  private String fetchOnce(RestClient client, String query, String value, String correlationId) {
    return client
        .get()
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

  private static RestClient restClient(Duration connectTimeout, Duration readTimeout) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  /**
   * Counts no connection, a timeout and a 5xx as failures. A 4xx says nothing about G2's health and
   * is ignored; an unusable answer is parsed after the call, so it doesn't count either.
   */
  private static CircuitBreaker readerCircuit(
      ArticlesProperties.CircuitBreaker settings, Clock clock) {
    CircuitBreaker breaker =
        CircuitBreaker.of(
            "g2-articles-reader",
            CircuitBreakerConfig.custom()
                .slidingWindow(
                    settings.windowSize(),
                    settings.minimumCalls(),
                    CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .failureRateThreshold(settings.failureRatePercent())
                .waitDurationInOpenState(settings.openFor())
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .ignoreExceptions(HttpClientErrorException.class)
                .clock(clock)
                .build());
    breaker
        .getEventPublisher()
        .onStateTransition(event -> logStateChange(event.getStateTransition(), settings.openFor()));
    return breaker;
  }

  /** Once per change of state, never per request (#224). */
  private static void logStateChange(CircuitBreaker.StateTransition change, Duration openFor) {
    if (change.getToState() == CircuitBreaker.State.OPEN) {
      LOG.warn(
          "G2 articles reader circuit breaker {} to {}: G2 isn't asked and articles get no links"
              + " for {}",
          change.getFromState(),
          change.getToState(),
          openFor);
    } else {
      LOG.warn(
          "G2 articles reader circuit breaker {} to {}",
          change.getFromState(),
          change.getToState());
    }
  }

  /** Not retried: asking again won't fix an answer that isn't the expected JSON. */
  private static ArticleList parse(String body) {
    if (body == null || body.isBlank()) {
      throw new ArticlesUnavailableException("G2's articles API answered with an empty body");
    }
    try {
      return JSON.readValue(body, ArticleList.class);
    } catch (JacksonException exception) {
      throw new ArticlesUnavailableException(
          "G2's articles API answered with an unusable article list: "
              + exception.getOriginalMessage(),
          exception);
    }
  }
}
