package se.rainmaker.publishercontrol.links;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.rainmaker.publishercontrol.articles.ArticlesUnavailableException;

/**
 * {@code GET /api/approved-links?url=…} for G1 (#150, contract 7.3). Internal: no login between the
 * apps, localhost only in the demo (contract 7).
 *
 * <ul>
 *   <li>{@code 200}: the article's approved links, or {@code "links": []}.
 *   <li>{@code 400}: {@code url} is missing or not an http(s) address.
 *   <li>{@code 503}: G2 can't say whether the article is still green, so there are no links.
 * </ul>
 */
@RestController
class ApprovedLinksController {

  private static final Logger LOG = LoggerFactory.getLogger(ApprovedLinksController.class);

  private final ApprovedLinks approvedLinks;

  ApprovedLinksController(ApprovedLinks approvedLinks) {
    this.approvedLinks = approvedLinks;
  }

  @GetMapping("/api/approved-links")
  ApprovedLinksAnswer approvedLinks(
      @RequestParam String url,
      @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {
    if (!url.startsWith("https://") && !url.startsWith("http://")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url must be an http(s) address");
    }
    try (MDC.MDCCloseable correlation = MDC.putCloseable("correlation_id", correlationId)) {
      return approvedLinks.forArticle(url, correlationId);
    }
  }

  /** Fail closed: no links when G2 can't answer (contract 7.3). */
  @ExceptionHandler(ArticlesUnavailableException.class)
  ResponseEntity<Void> g2Unavailable(ArticlesUnavailableException unavailable) {
    LOG.warn("No approved links: {}", unavailable.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }
}
