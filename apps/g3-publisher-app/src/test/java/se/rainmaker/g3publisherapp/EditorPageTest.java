package se.rainmaker.g3publisherapp;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static se.rainmaker.g3publisherapp.FakeG2Articles.HOME_OFFER;
import static se.rainmaker.g3publisherapp.FakeG2Articles.PUBLISHED_AT;
import static se.rainmaker.g3publisherapp.FakeG2Articles.TRAVEL_OFFER;
import static se.rainmaker.g3publisherapp.FakeG2Articles.article;
import static se.rainmaker.g3publisherapp.FakeG2Articles.notClassified;
import static se.rainmaker.g3publisherapp.FakeG2Articles.suggestion;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import se.rainmaker.publishercontrol.articles.ArticleSync;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;

/**
 * The editor page (#150, contract 7.6): recent articles with their zone, reason and suggestions,
 * and the Approve and Reject buttons. The articles come from a fake G2; the offers are G3's real
 * fictional ones; the database is real (Testcontainers).
 */
@SpringBootTest(properties = "rainmaker.publisher-control.articles.polling=false")
@AutoConfigureMockMvc
class EditorPageTest {

  private static final String PAGE = "/publisher/articles";

  @RegisterExtension
  static WireMockExtension g2 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    registry.add("rainmaker.publisher-control.articles.base-url", g2::baseUrl);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ArticleSync sync;

  @Autowired
  @Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient database;

  @Test
  void classifiedAndMatchedArticleShowsUpWithItsSuggestions() throws Exception {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    UUID travel = UUID.randomUUID();
    g2Sends(
        article(
            article,
            1,
            "green",
            suggestion(home, HOME_OFFER, 1),
            suggestion(travel, TRAVEL_OFFER, 2)));

    String section = articleOnPage(article, null);

    assertThat(section)
        .contains("Badrummet som håller i 40 år")
        .contains("Grön")
        .contains("Praktiska råd.")
        .contains("Vintra Kakel &amp; Bad")
        .contains("Gratis hembesök för badrumsoffert")
        .contains("Nordsol Resor")
        .contains("Passar artikelns ämne: Hem och bostad")
        .contains("action=\"/publisher/suggestions/" + home + "/approve\"")
        .contains("action=\"/publisher/suggestions/" + travel + "/reject\"");
  }

  @Test
  void redArticleIsListedWithItsZoneButNoSuggestionsOrButtons() throws Exception {
    UUID article = UUID.randomUUID();
    g2Sends(article(article, 1, "red", suggestion(UUID.randomUUID(), HOME_OFFER, 1)));

    String section = articleOnPage(article, null);

    assertThat(section).contains("Röd").contains("Inga förslag").doesNotContain("<form");
  }

  @Test
  void articleThatIsNotClassifiedSaysSo() throws Exception {
    UUID waiting = UUID.randomUUID();
    UUID failed = UUID.randomUUID();
    g2Sends(notClassified(waiting, "unclassified"), notClassified(failed, "failed"));

    assertThat(articleOnPage(waiting, "en")).contains("Not classified yet");
    assertThat(articleOnPage(failed, "en")).contains("Couldn&#39;t be classified");
  }

  @Test
  void pageIsInEnglishWhenTheBrowserAsksForIt() throws Exception {
    UUID article = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(UUID.randomUUID(), HOME_OFFER, 1)));

    String section = articleOnPage(article, "en-GB,en;q=0.9");

    assertThat(section)
        .contains("Green")
        .contains("Practical advice.")
        .contains("Matches the article&#39;s topic: Home")
        .contains(">Approve<");
  }

  @Test
  void pageSaysThereIsNoLogin() throws Exception {
    assertThat(page(null)).contains("Ingen inloggning");
    assertThat(page("en")).contains("No login");
  }

  /** Headlines and reasons come from another app; they are always escaped. */
  @Test
  void textFromG2IsEscaped() throws Exception {
    UUID article = UUID.randomUUID();
    g2Sends(
        article(article, 1, "green")
            .replace("Badrummet som håller i 40 år", "<script>alert(1)</script>"));

    assertThat(articleOnPage(article, null))
        .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
        .doesNotContain("<script>alert");
  }

  @Test
  void approvingWritesAnAuditRowAndShowsThePageAgain() throws Exception {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));

    MockHttpServletResponse response =
        mockMvc
            .perform(post("/publisher/suggestions/" + home + "/approve"))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(303);
    assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(PAGE + "#s-" + home);
    assertThat(audit(home)).isEqualTo("approved by demo-editor");
    assertThat(articleOnPage(article, null)).contains("Godkänd");
  }

  @Test
  void rejectingWritesAnAuditRow() throws Exception {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));

    mockMvc.perform(post("/publisher/suggestions/" + home + "/reject"));

    assertThat(audit(home)).isEqualTo("rejected by demo-editor");
  }

  @Test
  void suggestionOfRedArticleCannotBeApproved() throws Exception {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));
    g2Sends(article(article, 1, "red"));

    MockHttpServletResponse response =
        mockMvc
            .perform(post("/publisher/suggestions/" + home + "/approve"))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .contains("kan inte längre")
        .contains("href=\"/publisher/articles\"");
    // Only the sync's withdrawal (#198); the refused approval writes no row.
    assertThat(audit(home)).isEqualTo("withdrawn by g2");
  }

  @Test
  void unknownSuggestionGivesNotFound() throws Exception {
    MockHttpServletResponse response =
        mockMvc
            .perform(post("/publisher/suggestions/" + UUID.randomUUID() + "/reject"))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(404);
  }

  /**
   * The page lists the most recently published articles, and the database is shared with the other
   * test classes, so this test's articles are published later than theirs: always on the page.
   */
  private void g2Sends(String... articles) {
    String[] recent = new String[articles.length];
    for (int index = 0; index < articles.length; index++) {
      recent[index] =
          articles[index].replace(PUBLISHED_AT, "\"publishedAt\": \"2099-01-01T00:00:00Z\"");
    }
    FakeG2Articles.g2Sends(g2, sync, recent);
  }

  /** The whole page, in the language the header asks for ({@code null}: no header). */
  private String page(String acceptLanguage) throws Exception {
    var request = get(PAGE);
    if (acceptLanguage != null) {
      request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
    }
    MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
    return response.getContentAsString(StandardCharsets.UTF_8);
  }

  /** The part of the page about one article, from its {@code <article>} tag to the end tag. */
  private String articleOnPage(UUID article, String acceptLanguage) throws Exception {
    String page = page(acceptLanguage);
    int start = page.indexOf("<article id=\"a-" + article + "\"");
    assertThat(start).as("article %s on the page", article).isNotNegative();
    return page.substring(start, page.indexOf("</article>", start));
  }

  /** The suggestion's audit rows, oldest first, as {@code action by editor}, comma-separated. */
  private String audit(UUID suggestion) {
    return String.join(
        ",",
        database
            .sql(
                "SELECT action || ' by ' || decided_by FROM suggestion_audit"
                    + " WHERE suggestion_id = ? ORDER BY decided_at, audit_id")
            .param(suggestion)
            .query(String.class)
            .list());
  }
}
