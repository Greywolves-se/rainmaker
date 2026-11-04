package se.rainmaker.matchingranking.offers;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import se.rainmaker.matchingranking.ranking.OfferForMatching;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the offers from G3's API, {@code GET /api/offers} (#145, contract 7.2), with a connect and
 * a read timeout and retries with back-off (CLAUDE.md, "External calls"). Only a problem that may
 * go away is retried: no connection, a timeout or a 5xx. A 4xx or an unusable answer is not.
 */
@Component
public class G3OffersClient {

  private static final String OFFERS_PATH = "/api/offers";

  /** G3 sends more fields than matching needs; those are ignored, never an error. */
  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final RestClient http;
  private final URI offersUrl;
  private final Retry retry;

  /** Builds the HTTP client and the retry policy from the settings. */
  public G3OffersClient(OffersProperties properties) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    requestFactory.setReadTimeout(properties.readTimeout());
    this.http = RestClient.builder().requestFactory(requestFactory).build();
    this.offersUrl = properties.baseUrl().resolve(OFFERS_PATH);
    this.retry =
        Retry.of(
            "g3-offers",
            RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.backoff(), 2.0))
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
  }

  /**
   * Every offer, as G3 has it now.
   *
   * @throws OffersUnavailableException when G3 can't deliver after the retries
   */
  public List<OfferForMatching> offers() {
    String body;
    try {
      body = retry.executeSupplier(this::fetchOnce);
    } catch (RestClientException exception) {
      throw new OffersUnavailableException(
          "G3's offers API failed: " + exception.getMessage(), exception);
    }
    OfferList answer = parse(body);
    if (answer == null || answer.offers() == null) {
      throw new OffersUnavailableException("G3's offers API answered without an offer list");
    }
    return answer.offers().stream()
        .map(offer -> new OfferForMatching(offer.offerId(), offer.category(), offer.cpcOre()))
        .toList();
  }

  /**
   * The answer's text. Read whole, then parsed, as in the collector's GitHub client: parsing while
   * the JDK client's timed body stream is read failed with "Stream closed".
   */
  private String fetchOnce() {
    return http.get().uri(offersUrl).retrieve().body(String.class);
  }

  /** Not retried: asking again won't fix an answer that isn't the expected JSON. */
  private static OfferList parse(String body) {
    if (body == null || body.isBlank()) {
      throw new OffersUnavailableException("G3's offers API answered with an empty body");
    }
    try {
      return JSON.readValue(body, OfferList.class);
    } catch (JacksonException exception) {
      throw new OffersUnavailableException(
          "G3's offers API answered with invalid JSON: " + exception.getOriginalMessage(),
          exception);
    }
  }

  /** The answer's shape; G3 sends more fields, which are ignored. */
  record OfferList(List<OfferJson> offers) {}

  /** One offer in the answer. */
  record OfferJson(UUID offerId, String category, int cpcOre) {}
}
