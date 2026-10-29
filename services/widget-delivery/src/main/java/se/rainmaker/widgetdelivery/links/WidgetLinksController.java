package se.rainmaker.widgetdelivery.links;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /widget/v1/links?url=…}: the widget API rainmaker.js calls on a headline hover (#151,
 * contract 7.4, D-7). Public and read-only: no login, no cookies, and nothing about the reader is
 * stored or logged.
 *
 * <p>200 with the links, or an empty list for an article that may have none; 400 when {@code url}
 * isn't an absolute http(s) address on one of the publisher sites, or is too long (#224); 503 when
 * G3 can't answer, never the last known links (fail closed). rainmaker.js shows no panel for
 * anything but a 200 with links (fail open, #152).
 */
@RestController
class WidgetLinksController {

  /** The path, also used for its CORS rule ({@link WidgetApiConfiguration}). */
  static final String PATH = "/widget/v1/links";

  /**
   * The longest article address asked about. Longer ones are refused before G3 is called, so nobody
   * can make G1, G3 and G2 pass huge values along (#224).
   */
  static final int MAX_URL_LENGTH = 2048;

  private static final Logger LOG = LoggerFactory.getLogger(WidgetLinksController.class);

  private static final String CORRELATION_ID = "correlation_id";

  private final WidgetLinksService links;
  private final WidgetApiProperties settings;

  WidgetLinksController(WidgetLinksService links, WidgetApiProperties settings) {
    this.links = links;
    this.settings = settings;
  }

  @GetMapping(PATH)
  ResponseEntity<WidgetLinks> links(@RequestParam(name = "url", required = false) String url) {
    if (!isPublisherArticle(url)) {
      // Never the value itself: it is whatever the request sent.
      LOG.info(
          "Widget links refused: url is missing, too long, or not an http(s) address on a"
              + " publisher site");
      return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
    }
    // G1 has no article correlation id yet, so each widget request starts its own (contract 7).
    String correlationId = UUID.randomUUID().toString();
    MDC.put(CORRELATION_ID, correlationId);
    try {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .body(links.find(url, correlationId));
    } catch (ApprovedLinksUnavailableException unavailable) {
      logUnavailable(unavailable);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .cacheControl(CacheControl.noStore())
          .build();
    } finally {
      MDC.remove(CORRELATION_ID);
    }
  }

  /** While the circuit is open, the breaker has already said so once; not again on every hover. */
  private static void logUnavailable(ApprovedLinksUnavailableException unavailable) {
    if (unavailable.getCause() instanceof CallNotPermittedException) {
      LOG.debug("No widget links: {}", unavailable.getMessage());
    } else {
      LOG.warn("No widget links: {}", unavailable.getMessage());
    }
  }

  /**
   * Only an article on a publisher site can have links, so anything else is refused without asking
   * G3: random addresses could otherwise slow G3 and G2 and open the circuit for every reader.
   */
  private boolean isPublisherArticle(String url) {
    if (url == null || url.length() > MAX_URL_LENGTH) {
      return false;
    }
    try {
      URI uri = new URI(url);
      boolean http =
          "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
      return http && uri.getHost() != null && settings.isPublisherSite(uri);
    } catch (URISyntaxException notAddress) {
      return false;
    }
  }
}
