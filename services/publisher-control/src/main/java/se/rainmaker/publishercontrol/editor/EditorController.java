package se.rainmaker.publishercontrol.editor;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.rainmaker.publishercontrol.decision.SuggestionDecisions;
import se.rainmaker.publishercontrol.decision.SuggestionNotDecidableException;
import se.rainmaker.publishercontrol.decision.SuggestionNotFoundException;

/**
 * The editor page (#150, contract 7.6), under {@code /publisher/} so its paths don't clash with the
 * test site's own {@code /editor}. There is no login: the page is for the local demo only, it says
 * so, and every decision is saved as {@link #EDITOR}.
 *
 * <ul>
 *   <li>{@code GET /publisher/articles}: the recent articles with their zone, reason and
 *       suggestions.
 *   <li>{@code POST /publisher/suggestions/{id}/approve} and {@code …/reject}: decide, then go back
 *       to the page at that suggestion (303). A suggestion that is unknown (404) or can't be
 *       decided any more (409) gets a short page that says why.
 * </ul>
 */
@RestController
class EditorController {

  /** Who decided, until the editor page has a login. */
  static final String EDITOR = "demo-editor";

  private static final String PAGE = "/publisher/articles";
  private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
  private static final Locale SWEDISH = Locale.forLanguageTag("sv");
  private static final List<Locale> LANGUAGES = List.of(SWEDISH, Locale.ENGLISH);

  private final EditorArticles articles;
  private final EditorPage page;
  private final SuggestionDecisions decisions;

  EditorController(EditorArticles articles, EditorPage page, SuggestionDecisions decisions) {
    this.articles = articles;
    this.page = page;
    this.decisions = decisions;
  }

  @GetMapping(PAGE)
  ResponseEntity<String> articles(
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    return html(HttpStatus.OK, page.articles(articles.recent(), language(acceptLanguage)));
  }

  @PostMapping("/publisher/suggestions/{suggestionId}/approve")
  ResponseEntity<Void> approve(@PathVariable UUID suggestionId) {
    decisions.approve(suggestionId, EDITOR);
    return backTo(suggestionId);
  }

  @PostMapping("/publisher/suggestions/{suggestionId}/reject")
  ResponseEntity<Void> reject(@PathVariable UUID suggestionId) {
    decisions.reject(suggestionId, EDITOR);
    return backTo(suggestionId);
  }

  @ExceptionHandler(SuggestionNotFoundException.class)
  ResponseEntity<String> notFound(HttpServletRequest request) {
    return html(HttpStatus.NOT_FOUND, page.problem("error.not-found", language(request)));
  }

  @ExceptionHandler(SuggestionNotDecidableException.class)
  ResponseEntity<String> notDecidable(HttpServletRequest request) {
    return html(HttpStatus.CONFLICT, page.problem("error.not-decidable", language(request)));
  }

  /** Post/redirect/get: a reload of the page doesn't send the decision again. */
  private static ResponseEntity<Void> backTo(UUID suggestionId) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(PAGE + "#s-" + suggestionId))
        .build();
  }

  private static ResponseEntity<String> html(HttpStatus status, String body) {
    return ResponseEntity.status(status)
        .contentType(HTML)
        // The language depends on this header, so a cache must not reuse the page for another.
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
        .body(body);
  }

  private static Locale language(HttpServletRequest request) {
    return language(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
  }

  /**
   * English only when the browser asks for it, otherwise Swedish, as on S4's landing pages. Not
   * Spring's request locale: with no header, that is the server's own language.
   */
  static Locale language(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return SWEDISH;
    }
    try {
      Locale match = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), LANGUAGES);
      return match == null ? SWEDISH : match;
    } catch (IllegalArgumentException malformedHeader) {
      // A broken header is the browser's problem; the page is still shown, in Swedish.
      return SWEDISH;
    }
  }
}
