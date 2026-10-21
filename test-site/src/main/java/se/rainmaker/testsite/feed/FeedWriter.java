package se.rainmaker.testsite.feed;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import se.rainmaker.testsite.config.SiteProperties;
import se.rainmaker.testsite.content.Article;
import se.rainmaker.testsite.content.ArticleRepository;
import se.rainmaker.testsite.content.LiveContent;

/**
 * The machine-readable outputs that Rainmaker's Content Ingestion (S5) and the AI evaluation use -
 * the same things a real publisher's CMS offers. They are:
 *
 * <ul>
 *   <li>RSS 2.0 feed with every article
 *   <li>Google News-style sitemap
 *   <li>robots.txt pointing to the sitemap
 *   <li>evaluation labels: the "right answer" (expected zone) per article, for testing the AI
 *       classification (S6). A real publisher would not have this.
 * </ul>
 *
 * <p>No output uses the current time - dates come from the articles - so the same content always
 * gives exactly the same output (good for caching and for tests).
 */
@Component
public class FeedWriter {

  private static final String DUBLIN_CORE = "http://purl.org/dc/elements/1.1/";
  private static final String ATOM = "http://www.w3.org/2005/Atom";
  private static final String SITEMAP = "http://www.sitemaps.org/schemas/sitemap/0.9";
  private static final String NEWS = "http://www.google.com/schemas/sitemap-news/0.9";

  private final SiteProperties site;
  private final LiveContent liveContent;
  private final MessageSource messages;

  /** Creates the writer. */
  public FeedWriter(SiteProperties site, LiveContent liveContent, MessageSource messages) {
    this.site = site;
    this.liveContent = liveContent;
    this.messages = messages;
  }

  /** The RSS 2.0 feed. */
  public String rss(Locale locale) {
    ArticleRepository articles = liveContent.current().articles();
    OffsetDateTime lastBuild =
        articles
            .latestPublishedAt()
            .orElse(OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    return XmlDocument.build(
        xml -> {
          xml.start("rss")
              .attribute("version", "2.0")
              .namespace("dc", DUBLIN_CORE)
              .namespace("atom", ATOM);
          xml.start("channel")
              .element("title", message("site.name", locale))
              .element("link", site.absoluteUrl("/"))
              .emptyElement(
                  "atom",
                  "link",
                  ATOM,
                  "href",
                  site.absoluteUrl("/rss.xml"),
                  "rel",
                  "self",
                  "type",
                  "application/rss+xml")
              .element("description", message("feed.description", locale))
              .element("language", locale.getLanguage())
              .element("lastBuildDate", rfc822(lastBuild));
          for (Article article : articles.all()) {
            xml.start("item")
                .element("title", article.headline())
                .element("link", site.absoluteUrl(article.path()))
                .elementWithAttribute("guid", "isPermaLink", "false", article.id())
                .element("pubDate", rfc822(article.publishedAt()))
                .element("atom", "updated", ATOM, iso(article.lastModified()))
                .element("category", message(article.section().messageKey(), locale))
                .element("dc", "creator", DUBLIN_CORE, article.author().name())
                .element("description", article.lead())
                .end();
          }
          xml.end().end();
        });
  }

  /**
   * The news sitemap. Note: Google only wants articles from the last two days in a real news
   * sitemap; we list all of them, because the test articles are old and Rainmaker should find every
   * article.
   */
  public String newsSitemap(Locale locale) {
    ArticleRepository articles = liveContent.current().articles();
    return XmlDocument.build(
        xml -> {
          xml.start("urlset").namespace("", SITEMAP).namespace("news", NEWS);
          for (Article article : articles.all()) {
            xml.start("url")
                .element("loc", site.absoluteUrl(article.path()))
                .element("lastmod", iso(article.lastModified()));
            xml.start("news", "news", NEWS);
            xml.start("news", "publication", NEWS)
                .element("news", "name", NEWS, message("site.name", locale))
                .element("news", "language", NEWS, locale.getLanguage())
                .end();
            xml.element("news", "publication_date", NEWS, iso(article.publishedAt()))
                .element("news", "title", NEWS, article.headline())
                .end()
                .end();
          }
          xml.end();
        });
  }

  /**
   * The robots.txt file. Crawling is allowed so Rainmaker can read the site; the pages themselves
   * say "noindex" to stay out of search results.
   */
  public String robotsTxt() {
    return "User-agent: *\nAllow: /\n\nSitemap: " + site.absoluteUrl("/sitemap.xml") + "\n";
  }

  /** CSV with one row per article: id, expected zone, section and URL. */
  public String evaluationLabels() {
    ArticleRepository articles = liveContent.current().articles();
    Stream<List<String>> header =
        Stream.of(List.of("article_id", "expected_zone", "section", "url"));
    Stream<List<String>> rows =
        articles.all().stream()
            .map(
                article ->
                    List.of(
                        article.id(),
                        article.expectedZone().id(),
                        article.section().id(),
                        site.absoluteUrl(article.path())));
    return Stream.concat(header, rows)
            .map(row -> row.stream().map(FeedWriter::csvField).collect(Collectors.joining(",")))
            .collect(Collectors.joining("\n"))
        + "\n";
  }

  /** Quotes a CSV field when it contains a comma, quote or line break. */
  static String csvField(String value) {
    boolean needsQuotes =
        value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
    return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
  }

  /** RFC 822 date as RSS requires, e.g. "Tue, 13 Oct 2026 06:15:00 GMT". */
  static String rfc822(OffsetDateTime dateTime) {
    return DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime.atZoneSameInstant(ZoneOffset.UTC));
  }

  /** ISO 8601 with seconds, e.g. "2026-10-13T09:40:00+02:00". */
  static String iso(OffsetDateTime dateTime) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
  }

  private String message(String key, Locale locale) {
    return messages.getMessage(key, null, locale);
  }
}
