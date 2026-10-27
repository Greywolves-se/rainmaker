package se.rainmaker.g3publisherapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static se.rainmaker.g3publisherapp.FakeG2Articles.ARTICLES;
import static se.rainmaker.g3publisherapp.FakeG2Articles.HOME_OFFER;
import static se.rainmaker.g3publisherapp.FakeG2Articles.TRAVEL_OFFER;
import static se.rainmaker.g3publisherapp.FakeG2Articles.article;
import static se.rainmaker.g3publisherapp.FakeG2Articles.suggestion;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import se.rainmaker.publishercontrol.articles.ArticleSync;
import se.rainmaker.publishercontrol.decision.SuggestionDecisions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The approved links API for G1 (#150, contract 5 and 7.3): only approved suggestions of the
 * article's latest version, and only while G2 says, at that moment, that it is classified + green.
 * The articles come from a fake G2; the offers are G3's real fictional ones.
 */
@SpringBootTest(properties = "rainmaker.publisher-control.articles.polling=false")
@AutoConfigureMockMvc
class ApprovedLinksTest {

  private static final String API = "/api/approved-links";
  private static final String EDITOR = "demo-editor";

  @RegisterExtension
  static WireMockExtension g2 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    registry.add("rainmaker.publisher-control.articles.base-url", g2::baseUrl);
    registry.add("rainmaker.publisher-control.articles.backoff", () -> "10ms");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private ArticleSync sync;
  @Autowired private SuggestionDecisions decisions;

  @Test
  void approvedSuggestionIsAvailableAndRejectedOneIsNot() throws Exception {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    UUID travel = UUID.randomUUID();
    String green =
        article(
            article,
            1,
            "green",
            suggestion(home, HOME_OFFER, 1),
            suggestion(travel, TRAVEL_OFFER, 2));
    FakeG2Articles.g2Sends(g2, sync, green);
    decisions.approve(home, EDITOR);
    decisions.reject(travel, EDITOR);
    g2Knows(article, green);

    JsonNode answer = links(article);

    assertThat(answer.get("articleId").asString()).isEqualTo(article.toString());
    assertThat(answer.get("articleUrl").asString()).isEqualTo(url(article));
    assertThat(answer.get("links")).hasSize(1);
    JsonNode link = answer.get("links").get(0);
    assertThat(link.get("offerId").asString()).isEqualTo(HOME_OFFER.toString());
    assertThat(link.get("rank").asInt()).isEqualTo(1);
    assertThat(link.get("advertiser").asString()).isEqualTo("Vintra Kakel & Bad");
    assertThat(link.get("title").asString()).isEqualTo("Gratis hembesök för badrumsoffert");
    assertThat(link.get("text").asString()).isNotBlank();
    assertThat(link.get("destinationUrl").asString()).endsWith("/landing/" + HOME_OFFER);
  }

  @Test
  void suggestionThatIsNotApprovedYetIsNotAvailable() throws Exception {
    UUID article = UUID.randomUUID();
    String green = article(article, 1, "green", suggestion(UUID.randomUUID(), HOME_OFFER, 1));
    FakeG2Articles.g2Sends(g2, sync, green);
    g2Knows(article, green);

    assertThat(links(article).get("links")).isEmpty();
  }

  /** Rule 3: G2's answer now decides, even before G3's copy has seen the change. */
  @Test
  void articleThatG2NowSaysIsRedHasNoLinks() throws Exception {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    FakeG2Articles.g2Sends(g2, sync, article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));
    decisions.approve(home, EDITOR);
    g2Knows(article, article(article, 1, "red"));

    assertThat(links(article).get("links")).isEmpty();
  }

  /** Rule 2: an approval of an earlier version doesn't cover the new text (D-5). */
  @Test
  void approvalOfEarlierVersionIsNotAvailable() throws Exception {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    FakeG2Articles.g2Sends(g2, sync, article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));
    decisions.approve(home, EDITOR);
    g2Knows(article, article(article, 2, "green"));

    assertThat(links(article).get("links")).isEmpty();
  }

  @Test
  void unknownArticleHasNoIdAndNoLinks() throws Exception {
    UUID article = UUID.randomUUID();
    g2.stubFor(
        WireMock.get(urlPathEqualTo(ARTICLES))
            .withQueryParam("url", equalTo(url(article)))
            .willReturn(okJson("{\"articles\": []}")));

    JsonNode answer = links(article);

    assertThat(answer.get("articleId").isNull()).isTrue();
    assertThat(answer.get("links")).isEmpty();
  }

  /** Fail closed: when G2 can't say whether the article is still green, there are no links. */
  @Test
  void whenG2IsDownTheAnswerIs503() throws Exception {
    UUID article = UUID.randomUUID();
    g2.stubFor(
        WireMock.get(urlPathEqualTo(ARTICLES))
            .withQueryParam("url", equalTo(url(article)))
            .willReturn(aResponse().withStatus(503)));

    MockHttpServletResponse response = request(url(article), null);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("links");
  }

  @Test
  void passesTheCorrelationIdOnToG2() throws Exception {
    UUID article = UUID.randomUUID();
    g2Knows(article, article(article, 1, "green"));

    request(url(article), "c0a1-test");

    g2.verify(
        getRequestedFor(urlPathEqualTo(ARTICLES))
            .withQueryParam("url", equalTo(url(article)))
            .withHeader("X-Correlation-Id", equalTo("c0a1-test")));
  }

  @Test
  void urlThatIsMissingOrNotWebAddressIsBadRequest() throws Exception {
    assertThat(mockMvc.perform(get(API)).andReturn().getResponse().getStatus()).isEqualTo(400);
    assertThat(request("javascript:alert(1)", null).getStatus()).isEqualTo(400);
  }

  /** Makes the fake G2 answer {@code ?url=} for this article with this version of it. */
  private static void g2Knows(UUID article, String articleJson) {
    g2.stubFor(
        WireMock.get(urlPathEqualTo(ARTICLES))
            .withQueryParam("url", equalTo(url(article)))
            .willReturn(okJson("{\"articles\": [" + articleJson + "]}")));
  }

  /** The article's address, as {@link FakeG2Articles#article} writes it. */
  private static String url(UUID article) {
    return "http://localhost:3000/artikel/" + article;
  }

  private JsonNode links(UUID article) throws Exception {
    MockHttpServletResponse response = request(url(article), null);
    assertThat(response.getStatus()).isEqualTo(200);
    return jsonMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
  }

  private MockHttpServletResponse request(String url, String correlationId) throws Exception {
    var request = get(API).param("url", url);
    if (correlationId != null) {
      request.header("X-Correlation-Id", correlationId);
    }
    return mockMvc.perform(request).andReturn().getResponse();
  }
}
