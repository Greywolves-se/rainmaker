package se.rainmaker.trackingclick.click;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import se.rainmaker.trackingclick.click.ClickCounters.Result;
import se.rainmaker.trackingclick.click.ClickErrorPages.Reason;

/**
 * {@code GET /c/{token}}: opens a click link (#153, contract 7.5). A valid link is stored once per
 * click id and redirected (302) to the destination inside it; a forged, changed or garbled one gets
 * a 400 page and an expired one a 410 page, and neither stores anything. {@code HEAD} gets the same
 * answer but never stores a click (#192).
 *
 * <p>The target only ever comes from a link G1 signed, never from the request (no open redirect).
 * Nothing about the reader is stored or logged: no IP, no cookie, no user id.
 *
 * <p>Each answer is counted by result ({@link ClickCounters}, #193), and a valid link's log lines
 * carry its click id as {@code correlation_id} (contract section 3).
 */
@RestController
class ClickRedirectController {

  private static final Logger LOG = LoggerFactory.getLogger(ClickRedirectController.class);

  private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

  private static final String CORRELATION_ID = "correlation_id";

  private final SignedClickLinks links;
  private final ClickStore clicks;
  private final ClickCounters counters;
  private final ClickErrorPages errorPages = new ClickErrorPages();

  ClickRedirectController(SignedClickLinks links, ClickStore clicks, ClickCounters counters) {
    this.links = links;
    this.clicks = clicks;
    this.counters = counters;
  }

  @GetMapping(SignedClickLinks.PATH_PREFIX + "{token}")
  ResponseEntity<String> open(
      @PathVariable String token,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    return answer(token, acceptLanguage, link -> storeAndRedirect(link, acceptLanguage));
  }

  /**
   * {@code HEAD /c/{token}}: link checkers and e-mail and chat previews look at a link without a
   * reader opening it, so it's no click and nothing is stored (#192). They get the same status and
   * {@code Location} as {@code GET}, so the link still looks like it works.
   */
  @RequestMapping(path = SignedClickLinks.PATH_PREFIX + "{token}", method = RequestMethod.HEAD)
  ResponseEntity<String> look(
      @PathVariable String token,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    return answer(
        token,
        acceptLanguage,
        link -> {
          try (MDC.MDCCloseable correlation =
              MDC.putCloseable(CORRELATION_ID, link.clickId().toString())) {
            LOG.info("Click link looked at (HEAD), not stored: clickId={}", link.clickId());
            counters.count(Result.LOOKED_AT);
            return redirect(link);
          }
        });
  }

  private ResponseEntity<String> answer(
      String token, String acceptLanguage, Function<ClickLink, ResponseEntity<String>> onValid) {
    return switch (links.check(token)) {
      case ClickLinkCheck.Valid valid -> onValid.apply(valid.link());
      case ClickLinkCheck.Invalid invalid -> {
        counters.count(Result.INVALID);
        yield refused(HttpStatus.BAD_REQUEST, Reason.INVALID, acceptLanguage);
      }
      case ClickLinkCheck.Expired expired -> {
        counters.count(Result.EXPIRED);
        yield refused(HttpStatus.GONE, Reason.EXPIRED, acceptLanguage);
      }
    };
  }

  /** Every log line about this click, the error too, carries its click id as correlation id. */
  private ResponseEntity<String> storeAndRedirect(ClickLink link, String acceptLanguage) {
    try (MDC.MDCCloseable correlation =
        MDC.putCloseable(CORRELATION_ID, link.clickId().toString())) {
      return store(link, acceptLanguage);
    }
  }

  private ResponseEntity<String> store(ClickLink link, String acceptLanguage) {
    try {
      boolean stored = clicks.record(link);
      LOG.info(
          "{}: clickId={}, articleId={}, offerId={}",
          stored ? "Click stored" : "Click already stored",
          link.clickId(),
          link.articleId(),
          link.offerId());
      counters.count(stored ? Result.STORED : Result.ALREADY_STORED);
    } catch (DataAccessException e) {
      // Fail closed for money: a click that can't be stored is not passed on. Opening the same
      // link again retries with the same click id, so it is still stored once.
      LOG.error("Click not stored, so not redirected: clickId={}", link.clickId(), e);
      counters.count(Result.NOT_STORED);
      return page(HttpStatus.SERVICE_UNAVAILABLE, Reason.UNAVAILABLE, acceptLanguage);
    }
    return redirect(link);
  }

  private static ResponseEntity<String> redirect(ClickLink link) {
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
