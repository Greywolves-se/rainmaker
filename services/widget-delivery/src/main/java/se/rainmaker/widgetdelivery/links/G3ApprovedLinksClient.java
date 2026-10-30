package se.rainmaker.widgetdelivery.links;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks G3's approved links API, {@code GET /api/approved-links?url=…} (#151, contract 7.3), which
 * links an article may have right now. It runs on every widget request, so the timeouts are short
 * and there is one retry at most; while G3 keeps failing, a circuit breaker stops calling it, and
 * requests get "unavailable" at once instead of waiting.
 *
 * <p>Only a problem that may go away is retried and counted by the circuit breaker: no connection,
 * a timeout or a 5xx (G3 answers 503 when G2 can't say whether the article is green). A 4xx or an
 * unusable answer is neither.
 */
@Component
class G3ApprovedLinksClient {

  private static final Logger LOG = LoggerFactory.getLogger(G3ApprovedLinksClient.class);

  private static final String API_PATH = "/api/approved-links";
  private static final String CORRELATION_HEADER = "X-Correlation-Id";

  /** G3 may send more fields than S1 needs; those are ignored, never an error. */
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final RestClient http;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;

  /** Builds the HTTP client, the retry policy and the circuit breaker from the settings. */
  G3ApprovedLinksClient(ApprovedLinksProperties settings) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build());
    requestFactory.setReadTimeout(settings.readTimeout());
    this.http =
        RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(settings.baseUrl().toString())
            .build();
    this.retry =
        Retry.of(
            "g3-approved-links",
            RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(settings.backoff(), 2.0))
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
    this.circuitBreaker = circuitBreaker(settings.circuitBreaker());
  }

  /**
   * The links the article may have now.
   *
   * @param articleUrl the article's canonical address (contract D-2)
   * @param correlationId sent as {@code X-Correlation-Id} (contract 7), or {@code null}
   * @throws ApprovedLinksUnavailableException when G3 can't answer: no links (fail closed)
   */
  ApprovedLinks find(String articleUrl, String correlationId) {
    String body;
    try {
      body =
          circuitBreaker.executeSupplier(
              () -> retry.executeSupplier(() -> fetchOnce(articleUrl, correlationId)));
    } catch (CallNotPermittedException open) {
      throw new ApprovedLinksUnavailableException(
          "G3's approved links API isn't called while its circuit is open", open);
    } catch (RestClientException failed) {
      throw new ApprovedLinksUnavailableException(
          "G3's approved links API failed: " + failed.getMessage(), failed);
    }
    return toApprovedLinks(parse(body));
  }

  /**
   * The answer's text. Read whole, then parsed, as in matching's offers client: parsing while the
   * JDK client's timed body stream is read failed with "Stream closed".
   */
  private String fetchOnce(String articleUrl, String correlationId) {
    // A URI variable is encoded whole, so '&', '=' and '#' in the address stay in the one value.
    return http.get()
        .uri(uri -> uri.path(API_PATH).queryParam("url", "{url}").build(articleUrl))
        .headers(
            headers -> {
              if (correlationId != null) {
                headers.set(CORRELATION_HEADER, correlationId);
              }
            })
        .retrieve()
        .body(String.class);
  }

  /** Not retried: asking again won't fix an answer that isn't the agreed JSON. */
  private static Answer parse(String body) {
    if (body == null || body.isBlank()) {
      throw new ApprovedLinksUnavailableException("G3's approved links API answered empty");
    }
    Answer answer;
    try {
      answer = JSON.readValue(body, Answer.class);
    } catch (JacksonException invalid) {
      throw new ApprovedLinksUnavailableException(
          "G3's approved links API answered with invalid JSON: " + invalid.getOriginalMessage(),
          invalid);
    }
    if (answer == null || answer.links() == null) {
      throw new ApprovedLinksUnavailableException(
          "G3's approved links API answered without a link list");
    }
    return answer;
  }

  /** A link without one of its fields is left out and logged, never shown half-filled. */
  private static ApprovedLinks toApprovedLinks(Answer answer) {
    List<ApprovedLink> links =
        answer.links().stream()
            .filter(Objects::nonNull)
            .flatMap(
                link -> {
                  if (!link.isComplete()) {
                    LOG.warn("Left out a link from G3 without all its fields: {}", link.offerId());
                    return Stream.empty();
                  }
                  return Stream.of(
                      new ApprovedLink(
                          link.offerId(),
                          link.rank(),
                          link.advertiser(),
                          link.title(),
                          link.text(),
                          link.destinationUrl()));
                })
            .toList();
    return new ApprovedLinks(answer.articleId(), links);
  }

  /**
   * One call per widget request, around the retries. Each change of state is logged once, never
   * once per request.
   */
  private static CircuitBreaker circuitBreaker(ApprovedLinksProperties.CircuitBreaker settings) {
    CircuitBreaker breaker =
        CircuitBreaker.of(
            "g3-approved-links",
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
                .build());
    breaker
        .getEventPublisher()
        .onStateTransition(
            event ->
                LOG.warn(
                    "G3 approved links circuit breaker {}: while open, G3 isn't called and"
                        + " articles get no links for {}",
                    event.getStateTransition().getToState(),
                    settings.openFor()));
    return breaker;
  }

  /** The answer's shape (contract 7.3); G3 may send more fields, which are ignored. */
  record Answer(UUID articleId, List<LinkJson> links) {}

  /** One link in the answer, as G3 sends it. */
  record LinkJson(
      UUID offerId,
      Integer rank,
      String advertiser,
      String title,
      String text,
      String destinationUrl) {

    boolean isComplete() {
      return Stream.of(offerId, rank, advertiser, title, text, destinationUrl)
          .allMatch(Objects::nonNull);
    }
  }
}
