package se.rainmaker.contentingestion.feed;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RssFeedParserTest {

  /** The same shape as the test site's /rss.xml (#159). */
  static String feed(String items) {
    String template =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom"
             xmlns:dc="http://purl.org/dc/elements/1.1/">
          <channel>
            <title>Vintrabladet</title>
            <language>sv</language>
            %s
          </channel>
        </rss>
        """;
    return template.formatted(items);
  }

  static String item(String externalId, String url, String headline, String updated) {
    String template =
        """
        <item>
          <title>%s</title>
          <link>%s</link>
          <guid isPermaLink="false">%s</guid>
          <pubDate>Sat, 10 Oct 2026 07:45:00 +0200</pubDate>
          <atom:updated>%s</atom:updated>
          <category>Bostad</category>
          <description>Ingressen.</description>
        </item>
        """;
    return template.formatted(headline, url, externalId, updated);
  }

  private final RssFeedParser parser = new RssFeedParser();

  @Test
  void readsLanguageAndEveryItem() {
    String xml =
        feed(
            item(
                    "vb-0006",
                    "http://localhost:3000/artikel/badrum",
                    "Badrummet som håller",
                    "2026-10-12T09:00:00+02:00")
                + item(
                    "vb-0007",
                    "http://localhost:3000/artikel/elbil",
                    "Elbilen",
                    "2026-10-10T07:45:00+02:00"));

    Feed parsed = parser.parse(xml.getBytes(UTF_8));

    assertThat(parsed.language()).isEqualTo("sv");
    assertThat(parsed.items())
        .containsExactly(
            new FeedItem(
                "http://localhost:3000/artikel/badrum",
                "vb-0006",
                "Badrummet som håller",
                Instant.parse("2026-10-10T05:45:00Z"),
                Instant.parse("2026-10-12T07:00:00Z")),
            new FeedItem(
                "http://localhost:3000/artikel/elbil",
                "vb-0007",
                "Elbilen",
                Instant.parse("2026-10-10T05:45:00Z"),
                Instant.parse("2026-10-10T05:45:00Z")));
  }

  @Test
  void usesPublishTimeWhenItemHasNoUpdateTime() {
    String xml =
        feed(
            """
            <item>
              <title>Utan uppdatering</title>
              <link>http://localhost:3000/artikel/utan</link>
              <pubDate>Sat, 10 Oct 2026 07:45:00 +0200</pubDate>
            </item>
            """);

    FeedItem only = parser.parse(xml.getBytes(UTF_8)).items().getFirst();

    assertThat(only.lastModified()).isEqualTo(only.publishedAt());
    assertThat(only.externalId()).isNull();
  }

  @Test
  void skipsItemsWithoutLinkTitleOrValidDate() {
    String xml =
        feed(
            """
            <item><title>Ingen länk</title><pubDate>Sat, 10 Oct 2026 07:45:00 +0200</pubDate></item>
            <item><link>http://localhost:3000/artikel/x</link><pubDate>Sat, 10 Oct 2026 07:45:00 +0200</pubDate></item>
            <item><title>Fel datum</title><link>http://localhost:3000/artikel/y</link><pubDate>igår</pubDate></item>
            """
                + item(
                    "vb-0001",
                    "http://localhost:3000/artikel/ok",
                    "OK",
                    "2026-10-10T07:45:00+02:00"));

    assertThat(parser.parse(xml.getBytes(UTF_8)).items())
        .extracting(FeedItem::url)
        .containsExactly("http://localhost:3000/artikel/ok");
  }

  @Test
  void refusesTextThatIsNotXml() {
    assertThatThrownBy(() -> parser.parse("<html>Bad Gateway".getBytes(UTF_8)))
        .isInstanceOf(InvalidFeedException.class);
  }

  @Test
  void refusesXmlThatIsNotRss() {
    assertThatThrownBy(() -> parser.parse("<feed/>".getBytes(UTF_8)))
        .isInstanceOf(InvalidFeedException.class)
        .hasMessageContaining("channel");
  }

  @Test
  void refusesDocumentTypeDeclarations() {
    // A DOCTYPE could pull in external entities (XXE); a news feed never needs one.
    String xml =
        """
        <?xml version="1.0"?>
        <!DOCTYPE rss [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
        <rss><channel><language>&secret;</language></channel></rss>
        """;

    assertThatThrownBy(() -> parser.parse(xml.getBytes(UTF_8)))
        .isInstanceOf(InvalidFeedException.class);
  }
}
