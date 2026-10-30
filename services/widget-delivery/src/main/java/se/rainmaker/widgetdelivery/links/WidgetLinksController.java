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
 * {@code GET /widget/v1/links?url=…}: the widget API rainmaker.js calls to show an article's links
 * (#151, contract 7.4, D-7). Public and read-only: no login, no cookies, and nothing about the
 * reader is stored or logged.
 *
 * <p>200 with the links, or an empty list for an article that may have none; 400 when {@code url}
 * isn't an absolute http(s) address; 503 when G3 can't answer, never the last known links (fail
 * closed). rainmaker.js shows no panel for anything but a 200 with links (fail open, #152).
 */
@RestController
class WidgetLinksController {

  /** The path, also used for its CORS rule ({@link WidgetApiConfiguration}). */
  static final String PATH = "/widget/v1/links";

  private static final Logger LOG = LoggerFactory.getLogger(WidgetLinksController.class);

  private static final String CORRELATION_ID = "correlation_id";

  private final WidgetLinksService links;

  WidgetLinksController(WidgetLinksService links) {
    this.links = links;
  }

  @GetMapping(PATH)
  ResponseEntity<WidgetLinks> links(@RequestParam(name = "url", required = false) String url) {
    if (!isAbsoluteHttp(url)) {
      // Never the value itself: it is whatever the request sent.
      LOG.info("Widget links refused: url is missing or not an absolute http(s) address");
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

  /**
   * While the circuit is open, the breaker has already said so once; not again on every request.
   */
  private static void logUnavailable(ApprovedLinksUnavailableException unavailable) {
    if (unavailable.getCause() instanceof CallNotPermittedException) {
      LOG.debug("No widget links: {}", unavailable.getMessage());
    } else {
      LOG.warn("No widget links: {}", unavailable.getMessage());
    }
  }

  private static boolean isAbsoluteHttp(String url) {
    if (url == null) {
      return false;
    }
    try {
      URI uri = new URI(url);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null;
    } catch (URISyntaxException notAddress) {
      return false;
    }
  }
}
