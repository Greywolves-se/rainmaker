package se.rainmaker.publishercontrol.editor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import se.rainmaker.advertisermarketplace.api.Offer;
import se.rainmaker.advertisermarketplace.api.OfferCatalog;
import se.rainmaker.publishercontrol.editor.EditorArticles.EditorArticle;
import se.rainmaker.publishercontrol.editor.EditorArticles.EditorSuggestion;

/**
 * Writes the editor page's HTML (#150, contract 7.6). The labels come from {@code
 * publisher-control/messages*.properties}, in Swedish or English; text from G2 and the offers is
 * shown as it is, always HTML-escaped. The design is plain on purpose (#150).
 */
@Component
class EditorPage {

  /** Language, title, title, the no-login note, then the articles. */
  private static final String LAYOUT =
      """
      <!doctype html>
      <html lang="%s">
      <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <meta name="robots" content="noindex">
      <title>%s</title>
      <style>
      body { font-family: system-ui, sans-serif; max-width: 48rem; margin: 0 auto; padding: 1rem; }
      article { border-top: 1px solid #ccc; padding: 0.5rem 0 1rem; }
      .zone-green { color: #1f7a4d; } .zone-yellow { color: #8a5a00; } .zone-red { color: #b3261e; }
      li { margin: 0.5rem 0; } form { display: inline; }
      </style>
      </head>
      <body>
      <h1>%s</h1>
      <p><strong>%s</strong></p>
      <main>
      %s
      </main>
      </body>
      </html>
      """;

  private final OfferCatalog offers;
  private final ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

  EditorPage(OfferCatalog offers) {
    this.offers = offers;
    messages.setBasename("publisher-control/messages");
    messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
    // Swedish (messages.properties) unless the browser asks for English.
    messages.setFallbackToSystemLocale(false);
  }

  /** The whole page with these articles. The offers are read once, not once per suggestion. */
  String articles(List<EditorArticle> articles, Locale locale) {
    Map<UUID, Offer> offersById =
        offers.all().stream().collect(Collectors.toMap(Offer::offerId, Function.identity()));
    String body =
        articles.isEmpty()
            ? paragraph(message("page.empty", locale))
            : articles.stream()
                .map(article -> article(article, offersById, locale))
                .collect(Collectors.joining("\n"));
    return layout(locale, body);
  }

  /** A page with one message and a link back to the articles, for a decision that failed. */
  String problem(String messageKey, Locale locale) {
    return layout(
        locale,
        paragraph(message(messageKey, locale))
            + "\n<p><a href=\"/publisher/articles\">"
            + escaped(message("page.back", locale))
            + "</a></p>");
  }

  private String layout(Locale locale, String body) {
    String title = escaped(message("page.title", locale));
    return LAYOUT.formatted(
        locale.getLanguage(), title, title, escaped(message("page.no-login", locale)), body);
  }

  private String article(EditorArticle article, Map<UUID, Offer> offersById, Locale locale) {
    StringBuilder html = new StringBuilder();
    html.append("<article id=\"a-").append(article.articleId()).append("\">\n");
    html.append("<h2>").append(headline(article)).append("</h2>\n");
    html.append(state(article, locale));
    String reason = "en".equals(locale.getLanguage()) ? article.reasonEn() : article.reasonSv();
    if ("classified".equals(article.status()) && reason != null) {
      html.append(paragraph(message("article.reason", locale, reason)));
    }
    if (article.suggestions().isEmpty()) {
      html.append(paragraph(message("article.no-suggestions", locale)));
    } else {
      html.append("<ol>\n");
      article
          .suggestions()
          .forEach(suggestion -> html.append(suggestion(suggestion, offersById, locale)));
      html.append("</ol>\n");
    }
    return html.append("</article>").toString();
  }

  /** A link to the article only for a web address: a URL from another app is never trusted. */
  private static String headline(EditorArticle article) {
    String headline = escaped(article.headline());
    String url = article.url();
    boolean web = url.startsWith("https://") || url.startsWith("http://");
    return web ? "<a href=\"" + escaped(url) + "\">" + headline + "</a>" : headline;
  }

  /** The zone when classified, otherwise "Not classified yet" or "Couldn't be classified". */
  private String state(EditorArticle article, Locale locale) {
    if ("classified".equals(article.status())) {
      return "<p class=\"zone-"
          + escaped(article.zone())
          + "\"><strong>"
          + escaped(message("zone." + article.zone(), locale))
          + "</strong></p>\n";
    }
    return paragraph(message("status." + article.status(), locale));
  }

  private String suggestion(
      EditorSuggestion suggestion, Map<UUID, Offer> offersById, Locale locale) {
    Offer known = offersById.get(suggestion.offerId());
    String offer =
        known == null
            ? escaped(message("suggestion.unknown-offer", locale, suggestion.offerId()))
            : offer(known);
    String topics =
        suggestion.matchedTopics().stream()
            .map(topic -> messages.getMessage("topic." + topic, null, topic, locale))
            .collect(Collectors.joining(", "));
    StringBuilder html = new StringBuilder();
    html.append("<li id=\"s-").append(suggestion.suggestionId()).append("\">");
    html.append(offer).append("<br>\n");
    html.append(escaped(message("suggestion.matches", locale, topics))).append(" · ");
    html.append("<strong>")
        .append(escaped(message("suggestion." + suggestion.status(), locale)))
        .append("</strong>\n");
    // The button for the decision the suggestion already has is left out.
    if (!"approved".equals(suggestion.status())) {
      html.append(button(suggestion, "approve", locale));
    }
    if (!"rejected".equals(suggestion.status())) {
      html.append(button(suggestion, "reject", locale));
    }
    return html.append("</li>\n").toString();
  }

  private static String offer(Offer offer) {
    return "<strong>" + escaped(offer.advertiser()) + "</strong> – " + escaped(offer.title());
  }

  private String button(EditorSuggestion suggestion, String decision, Locale locale) {
    return "<form method=\"post\" action=\"/publisher/suggestions/"
        + suggestion.suggestionId()
        + "/"
        + decision
        + "\"><button type=\"submit\">"
        + escaped(message("suggestion." + decision, locale))
        + "</button></form>\n";
  }

  private String message(String key, Locale locale, Object... arguments) {
    return messages.getMessage(key, arguments, locale);
  }

  private static String paragraph(String text) {
    return "<p>" + escaped(text) + "</p>\n";
  }

  private static String escaped(String text) {
    return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
  }
}
