package se.rainmaker.trackingclick.click;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.rainmaker.trackingclick.click.ClickErrorPages.Reason;

/**
 * {@code GET /c/{token}}: opens a click link (#153, contract 7.5). A valid link is stored once per
 * click id and redirected (302) to the destination inside it; a forged, changed or garbled one gets
 * a 400 page and an expired one a 410 page, and neither stores anything.
 *
 * <p>The target only ever comes from a link G1 signed, never from the request (no open redirect).
 * Nothing about the reader is stored or logged: no IP, no cookie, no user id.
 */
@RestController
class ClickRedirectController {

  private static final Logger LOG = LoggerFactory.getLogger(ClickRedirectController.class);

  private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

  private final SignedClickLinks links;
  private final ClickStore clicks;
  private final ClickErrorPages errorPages = new ClickErrorPages();

  ClickRedirectController(SignedClickLinks links, ClickStore clicks) {
    this.links = links;
    this.clicks = clicks;
  }

  @GetMapping(SignedClickLinks.PATH_PREFIX + "{token}")
  ResponseEntity<String> open(
      @PathVariable String token,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    return switch (links.check(token)) {
      case ClickLinkCheck.Valid valid -> redirect(valid.link(), acceptLanguage);
      case ClickLinkCheck.Invalid invalid ->
          refused(HttpStatus.BAD_REQUEST, Reason.INVALID, acceptLanguage);
      case ClickLinkCheck.Expired expired ->
          refused(HttpStatus.GONE, Reason.EXPIRED, acceptLanguage);
    };
  }

  private ResponseEntity<String> redirect(ClickLink link, String acceptLanguage) {
    try {
      boolean stored = clicks.record(link);
      LOG.info(
          "{}: clickId={}, articleId={}, offerId={}",
          stored ? "Click stored" : "Click already stored",
          link.clickId(),
          link.articleId(),
          link.offerId());
    } catch (DataAccessException e) {
      // Fail closed for money: a click that can't be stored is not passed on. Opening the same
      // link again retries with the same click id, so it is still stored once.
      LOG.error("Click not stored, so not redirected: clickId={}", link.clickId(), e);
      return page(HttpStatus.SERVICE_UNAVAILABLE, Reason.UNAVAILABLE, acceptLanguage);
    }
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(link.destinationUrl()))
        .cacheControl(CacheControl.noStore())
        .build();
  }

  private ResponseEntity<String> refused(HttpStatus status, Reason reason, String acceptLanguage) {
    // Never the token itself: it is whatever the request sent.
    LOG.info("Click link refused: {}", reason);
    return page(status, reason, acceptLanguage);
  }

  private ResponseEntity<String> page(HttpStatus status, Reason reason, String acceptLanguage) {
    return ResponseEntity.status(status)
        .contentType(HTML)
        .cacheControl(CacheControl.noStore())
        // The language depends on this header, so a cache must not reuse the page for another.
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
        .body(errorPages.page(reason, acceptLanguage));
  }
}
