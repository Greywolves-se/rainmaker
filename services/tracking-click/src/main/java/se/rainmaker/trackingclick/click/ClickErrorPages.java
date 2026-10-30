package se.rainmaker.trackingclick.click;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.util.HtmlUtils;

/**
 * The page a reader sees when a click link can't be opened (#153, contract 7.5). The text comes
 * from {@code tracking-click/messages*.properties}: Swedish, or English if the browser asks for it.
 */
final class ClickErrorPages {

  /** Why the link couldn't be opened; each has a title and a text in the translation files. */
  enum Reason {
    INVALID("click.invalid"),
    EXPIRED("click.expired"),
    UNAVAILABLE("click.unavailable");

    private final String messageKey;

    Reason(String messageKey) {
      this.messageKey = messageKey;
    }
  }

  private static final Locale SWEDISH = Locale.forLanguageTag("sv");
  private static final List<Locale> LANGUAGES = List.of(SWEDISH, Locale.ENGLISH);

  /** Language, title, heading and text. */
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
      <main>
      <h1>%s</h1>
      <p>%s</p>
      </main>
      </body>
      </html>
      """;

  private final ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

  ClickErrorPages() {
    messages.setBasename("tracking-click/messages");
    messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
    // Swedish (messages.properties) unless the browser asks for English.
    messages.setFallbackToSystemLocale(false);
  }

  /** The page for this reason, in the language the {@code Accept-Language} header asks for. */
  String page(Reason reason, String acceptLanguage) {
    Locale locale = language(acceptLanguage);
    String title = escaped(messages.getMessage(reason.messageKey + ".title", null, locale));
    String text = escaped(messages.getMessage(reason.messageKey + ".text", null, locale));
    return PAGE.formatted(locale.getLanguage(), title, title, text);
  }

  /**
   * English only when the browser asks for it, otherwise Swedish; the same rule as S4's landing
   * pages. Not Spring's request locale: with no header, that is the server's own language.
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

  private static String escaped(String text) {
    return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
  }
}
