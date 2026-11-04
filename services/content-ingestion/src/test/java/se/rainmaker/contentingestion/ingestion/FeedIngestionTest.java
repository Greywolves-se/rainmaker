package se.rainmaker.contentingestion.ingestion;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.feed.RssFeedParser;
import se.rainmaker.contentingestion.page.ArticlePageParser;
import se.rainmaker.contentingestion.site.PublisherSite;

/**
 * #147's acceptance criteria against a WireMock RSS feed and a real PostgreSQL with the module's
 * own migrations: a new item is stored once, a repeated item is not stored again, an edited item
 * gives a new version, and a feed that is down or invalid is logged while the next poll still
 * works.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class FeedIngestionTest {

  private static final String FEED = "/rss.xml";
  private static final String BADRUM = "/artikel/badrum";
  private static final String ELBIL = "/artikel/elbil";

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @RegisterExtension
  static WireMockExtension site =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final List<ArticleStored> events = new ArrayList<>();
  private JdbcClient database;
  private FeedIngestion ingestion;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/content-ingestion")
            .cleanDisabled(false)
            .load();
    flyway.clean();
    flyway.migrate();
    database = JdbcClient.create(dataSource);

    URI feedUrl = URI.create(site.baseUrl() + FEED);
    ingestion =
        new FeedIngestion(
            feedUrl,
            new PublisherSite(Duration.ofSeconds(2)),
            new RssFeedParser(),
            new ArticlePageParser(),
            new ArticleVersions(database),
            events::add,
            Clock.systemUTC());
    servePage(BADRUM, "bostad", "Byt inte golvet i onödan.");
    servePage(ELBIL, "motor", "Räckvidden räcker.");
  }

  @Test
  void storesNewArticleOnceWithItsTextSectionAndLanguage() {
    serveFeed(item("vb-0006", BADRUM, "Badrummet som håller", "2026-10-10T07:45:00+02:00"));

    ingestion.pollOnce();
    ingestion.pollOnce();

    assertThat(storedVersions()).isEqualTo(1);
    assertThat(events).hasSize(1);
    ArticleStored stored = events.getFirst();
    assertThat(stored.version()).isEqualTo(1);
    assertThat(stored.url()).isEqualTo(site.baseUrl() + BADRUM);
    assertThat(stored.headline()).isEqualTo("Badrummet som håller");
    assertThat(stored.text()).isEqualTo("Byt inte golvet i onödan.");
    assertThat(stored.section()).isEqualTo("bostad");
    assertThat(stored.language()).isEqualTo("sv");
    assertThat(stored.publishedAt()).isEqualTo(Instant.parse("2026-10-10T05:45:00Z"));
    assertThat(stored.updatedAt()).isNull();
    // Ids are UUIDv7, sorted by creation time (contract section 3).
    assertThat(stored.articleId().version()).isEqualTo(7);
    assertThat(UUID.fromString(stored.correlationId()).version()).isEqualTo(7);
  }

  @Test
  void repeatedItemIsNotFetchedOrStoredAgain() {
    serveFeed(item("vb-0006", BADRUM, "Badrummet", "2026-10-10T07:45:00+02:00"));

    ingestion.pollOnce();
    ingestion.pollOnce();

    site.verify(1, getRequestedFor(urlEqualTo(BADRUM)));
    assertThat(storedVersions()).isEqualTo(1);
  }

  @Test
  void editedItemGivesNewVersionOfSameArticle() {
    serveFeed(item("vb-0006", BADRUM, "Badrummet", "2026-10-10T07:45:00+02:00"));
    ingestion.pollOnce();

    serveFeed(item("vb-0006", BADRUM, "Badrummet, uppdaterad", "2026-10-30T10:40:00+01:00"));
    ingestion.pollOnce();

    assertThat(storedVersions()).isEqualTo(2);
    assertThat(events).extracting(ArticleStored::version).containsExactly(1, 2);
    assertThat(events.get(1).articleId()).isEqualTo(events.get(0).articleId());
    assertThat(events.get(1).headline()).isEqualTo("Badrummet, uppdaterad");
    assertThat(events.get(1).updatedAt()).isEqualTo(Instant.parse("2026-10-30T09:40:00Z"));
  }

  @Test
  void feedThatIsDownIsLoggedAndNextPollStillWorks(CapturedOutput output) {
    site.stubFor(get(FEED).willReturn(aResponse().withStatus(503)));

    ingestion.pollOnce();

    assertThat(output).contains("Could not read the feed").contains("503");
    assertThat(storedVersions()).isZero();

    serveFeed(item("vb-0006", BADRUM, "Badrummet", "2026-10-10T07:45:00+02:00"));
    ingestion.pollOnce();

    assertThat(storedVersions()).isEqualTo(1);
  }

  @Test
  void invalidFeedIsLoggedAndNextPollStillWorks(CapturedOutput output) {
    site.stubFor(get(FEED).willReturn(aResponse().withBody("<html>Bad Gateway")));

    ingestion.pollOnce();

    assertThat(output).contains("Could not read the feed");
    assertThat(storedVersions()).isZero();

    serveFeed(item("vb-0006", BADRUM, "Badrummet", "2026-10-10T07:45:00+02:00"));
    ingestion.pollOnce();

    assertThat(storedVersions()).isEqualTo(1);
  }

  @Test
  void articlePageThatIsDownSkipsOnlyThatArticleUntilNextPoll(CapturedOutput output) {
    serveFeed(
        item("vb-0006", BADRUM, "Badrummet", "2026-10-10T07:45:00+02:00")
            + item("vb-0007", ELBIL, "Elbilen", "2026-10-10T07:45:00+02:00"));
    site.stubFor(get(BADRUM).willReturn(aResponse().withStatus(500)));

    ingestion.pollOnce();

    assertThat(output).contains("Skipped " + site.baseUrl() + BADRUM);
    assertThat(events).extracting(ArticleStored::section).containsExactly("motor");

    servePage(BADRUM, "bostad", "Nu fungerar sidan.");
    ingestion.pollOnce();

    assertThat(storedVersions()).isEqualTo(2);
  }

  @Test
  void skipsLinkThatIsNoValidAddressAndReadsTheRest(CapturedOutput output) {
    // A space makes the link an invalid URI; only someone else's feed could have one.
    serveFeed(
        item(
                "vb-0098",
                site.baseUrl() + "/artikel/med mellanslag",
                "Trasig",
                "2026-10-10T07:45:00+02:00")
            + item("vb-0006", BADRUM, "Badrummet", "2026-10-10T07:45:00+02:00"));

    ingestion.pollOnce();

    assertThat(output).contains("not a valid address");
    assertThat(events).extracting(ArticleStored::headline).containsExactly("Badrummet");
  }

  @Test
  void neverReadsPagesOnAnotherSite(CapturedOutput output) {
    serveFeed(
        item(
            "vb-0666",
            "http://169.254.169.254/latest/meta-data",
            "Fälla",
            "2026-10-10T07:45:00+02:00"));

    ingestion.pollOnce();

    assertThat(output).contains("not on the feed's site");
    assertThat(storedVersions()).isZero();
  }

  @Test
  void onlyReadsFromThePublishersSite() {
    serveFeed(item("vb-0006", BADRUM, "Badrummet", "2026-10-10T07:45:00+02:00"));

    ingestion.pollOnce();

    // Product rule: ingestion is read-only, it never writes to the publisher's site.
    assertThat(site.getAllServeEvents())
        .isNotEmpty()
        .extracting(ServeEvent::getRequest)
        .allSatisfy(request -> assertThat(request.getMethod()).isEqualTo(RequestMethod.GET));
  }

  private long storedVersions() {
    return database.sql("SELECT count(*) FROM article_version").query(Long.class).single();
  }

  private static String item(String externalId, String path, String headline, String updated) {
    String url = path.startsWith("http") ? path : site.baseUrl() + path;
    String template =
        """
        <item>
          <title>%s</title>
          <link>%s</link>
          <guid isPermaLink="false">%s</guid>
          <pubDate>Sat, 10 Oct 2026 07:45:00 +0200</pubDate>
          <atom:updated>%s</atom:updated>
          <description>Ingressen.</description>
        </item>
        """;
    return template.formatted(headline, url, externalId, updated);
  }

  private static void serveFeed(String items) {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
          <channel><title>Vintrabladet</title><language>sv</language>%s</channel>
        </rss>
        """
            .formatted(items);
    site.stubFor(
        get(FEED)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/rss+xml; charset=utf-8")
                    .withBody(xml)));
  }

  private static void servePage(String path, String section, String text) {
    String html =
        """
        <!doctype html><html lang="sv"><body>
        <article><a class="article__section" href="/sektion/%s">X</a><h1>Rubrik</h1><p>%s</p></article>
        </body></html>
        """
            .formatted(section, text);
    site.stubFor(
        get(path)
            .willReturn(
                aResponse().withHeader("Content-Type", "text/html; charset=utf-8").withBody(html)));
  }
}
