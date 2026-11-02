package se.rainmaker.g2contentapp.articles;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/articles?recent=N} and {@code ?url=…}: G2's articles for G3 (#202, contract 7.1).
 * G3 polls {@code ?recent=} for its editor page and asks {@code ?url=} for the current zone on each
 * widget request. Internal: no login, localhost only in the demo (contract 7).
 *
 * <p>200 with the articles (an empty list for an unknown URL); 400 unless exactly one of {@code
 * recent} (a whole number from 1 to 500) and {@code url} (not blank) is given.
 */
@RestController
class ArticlesController {

  /** The most articles one {@code ?recent=} may ask for. */
  static final int MAX_RECENT = 500;

  private static final Logger LOG = LoggerFactory.getLogger(ArticlesController.class);

  private static final String CORRELATION_ID = "correlation_id";

  /** Digits only: no sign, no decimals. Up to four, so 1000 is refused as too many, not as text. */
  private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]{1,4}");

  /** What a caller's correlation id may look like before it goes on a log line. */
  private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

  private final CurrentArticles articles;

  ArticlesController(CurrentArticles articles) {
    this.articles = articles;
  }

  @GetMapping("/api/articles")
  ResponseEntity<ArticleList> articles(
      @RequestParam(name = "recent", required = false) String recent,
      @RequestParam(name = "url", required = false) String url,
      @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {
    if (correlationId != null && SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
      MDC.put(CORRELATION_ID, correlationId);
    }
    try {
      if ((recent == null) == (url == null)) {
        return refused("give exactly one of recent and url");
      }
      if (url != null) {
        return url.isBlank() ? refused("url is blank") : answer(articles.byUrl(url));
      }
      int count = WHOLE_NUMBER.matcher(recent).matches() ? Integer.parseInt(recent) : 0;
      if (count < 1 || count > MAX_RECENT) {
        return refused("recent must be a whole number from 1 to " + MAX_RECENT);
      }
      return answer(articles.recent(count));
    } finally {
      MDC.remove(CORRELATION_ID);
    }
  }

  /** Never cached: G3 decides on the current zone (contract 7.3). */
  private static ResponseEntity<ArticleList> answer(ArticleList list) {
    LOG.debug("Answered with {} articles", list.articles().size());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(list);
  }

  /** Never the values themselves: they are whatever the request sent. */
  private static ResponseEntity<ArticleList> refused(String reason) {
    LOG.info("Articles request refused: {}", reason);
    return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
  }
}
