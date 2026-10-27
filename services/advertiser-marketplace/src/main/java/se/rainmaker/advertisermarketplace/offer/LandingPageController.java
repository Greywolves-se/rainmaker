package se.rainmaker.advertisermarketplace.offer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import se.rainmaker.advertisermarketplace.api.Offer;
import se.rainmaker.advertisermarketplace.api.OfferCatalog;

/**
 * {@code GET /landing/{offerId}}: one simple, fictional landing page per offer (#149), so a click
 * has a real page to land on. Like the test site, it says that it is fictional. The labels come
 * from {@code advertiser-marketplace/messages*.properties}, in Swedish or, if the browser asks for
 * it, English; the offer's own text is shown as it is stored, always HTML-escaped.
 *
 * <p>Demo only: these pages exist because every offer is fictional. Real advertisers will have
 * their own destination URL per offer, and the pages will then only be for test offers.
 */
@RestController
class LandingPageController {

  /** The landing pages' address, before the offer id. */
  static final String PATH_PREFIX = "/landing/";

  private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
  private static final Locale SWEDISH = Locale.forLanguageTag("sv");
  private static final List<Locale> LANGUAGES = List.of(SWEDISH, Locale.ENGLISH);

  /** Language, page title, fiction label, advertiser, title, text and category. */
  private static final String PAGE =
      """
      <!doctype html>
      <html lang="%s">
      <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <meta name="robots" content="noindex">
      <title>%s</title>
      </head>
      <body>
      <p><strong>%s</strong></p>
      <main>
      <p>%s</p>
      <h1>%s</h1>
      <p>%s</p>
      <p>%s</p>
      </main>
      </body>
      </html>
      """;

  private final OfferCatalog offers;
  private final ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

  LandingPageController(OfferCatalog offers) {
    this.offers = offers;
    messages.setBasename("advertiser-marketplace/messages");
    messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
    // Swedish (messages.properties) unless the browser asks for English.
    messages.setFallbackToSystemLocale(false);
  }

  @GetMapping(PATH_PREFIX + "{offerId}")
  ResponseEntity<String> landingPage(
      @PathVariable UUID offerId,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    Offer offer =
        offers
            .find(offerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No offer"));
    return ResponseEntity.ok()
        .contentType(HTML)
        // The language depends on this header, so a cache must not reuse the page for another.
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
        .body(page(offer, language(acceptLanguage)));
  }

  /**
   * English only when the browser asks for it, otherwise Swedish. Not Spring's request locale: with
   * no header, that is the server's own language.
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

  private String page(Offer offer, Locale locale) {
    String category = message("category." + offer.category(), locale);
    return PAGE.formatted(
        locale.getLanguage(),
        escaped(offer.title()),
        escaped(message("landing.fiction", locale)),
        escaped(offer.advertiser()),
        escaped(offer.title()),
        escaped(offer.text()),
        escaped(message("landing.category", locale, category)));
  }

  private String message(String key, Locale locale, Object... arguments) {
    return messages.getMessage(key, arguments, locale);
  }

  private static String escaped(String text) {
    return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
  }
}
