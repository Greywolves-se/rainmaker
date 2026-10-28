package se.rainmaker.contentingestion.page;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Reads the body text and the section from a server-rendered article page (#147). The RSS feed only
 * carries the lead.
 *
 * <p>The text is every {@code <p>} inside {@code <article>}: the test site keeps menus, "read also"
 * and ads outside it, and the kicker, byline and paywall offer contain no {@code <p>}. The section
 * is the last part of the article's {@code /sektion/<id>} link. Both are the test site's markup; a
 * real publisher needs its own rules.
 */
public class ArticlePageParser {

  private static final String SECTION_PATH = "/sektion/";

  /**
   * Parses the page's bytes; the page's own meta tag or byte order mark decides the character set.
   *
   * @param url the page's address, only for error messages
   * @throws InvalidArticlePageException when the page has no article, no text or no section
   */
  public ArticlePage parse(byte[] html, String url) {
    Document document = document(html, url);
    Element article = document.selectFirst("article");
    if (article == null) {
      throw new InvalidArticlePageException("No <article> on " + url);
    }
    return new ArticlePage(text(article, url), section(article, url));
  }

  private static Document document(byte[] html, String url) {
    try {
      return Jsoup.parse(new ByteArrayInputStream(html), null, url);
    } catch (IOException unreadable) {
      // Only reading the byte array, which cannot fail; kept for jsoup's signature.
      throw new UncheckedIOException(unreadable);
    }
  }

  private static String text(Element article, String url) {
    String text =
        article.select("p").stream()
            .map(Element::text)
            .map(String::strip)
            .filter(paragraph -> !paragraph.isEmpty())
            .collect(Collectors.joining("\n\n"));
    if (text.isEmpty()) {
      throw new InvalidArticlePageException("No text in the article on " + url);
    }
    return text;
  }

  private static String section(Element article, String url) {
    Elements links = article.select("a[href*=" + SECTION_PATH + "]");
    for (Element link : links) {
      String href = link.attr("href");
      String section = href.substring(href.indexOf(SECTION_PATH) + SECTION_PATH.length()).strip();
      if (!section.isEmpty() && !section.contains("/")) {
        return section;
      }
    }
    throw new InvalidArticlePageException("No section link in the article on " + url);
  }
}
