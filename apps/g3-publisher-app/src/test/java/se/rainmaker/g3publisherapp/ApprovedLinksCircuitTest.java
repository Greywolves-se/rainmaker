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
import static se.rainmaker.g3publisherapp.FakeG2Articles.article;
import static se.rainmaker.g3publisherapp.FakeG2Articles.suggestion;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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

/**
 * While G2 keeps failing, the approved links API stops asking it and answers 503 at once (#230):
 * never the links G3 gave before (fail closed, contract 7.3). Its own class, so the open circuit
 * can't affect the other approved links tests.
 */
@SpringBootTest(properties = "rainmaker.publisher-control.articles.polling=false")
@AutoConfigureMockMvc
class ApprovedLinksCircuitTest {

  private static final String API = "/api/approved-links";

  @RegisterExtension
  static WireMockExtension g2 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    registry.add("rainmaker.publisher-control.articles.base-url", g2::baseUrl);
    registry.add("rainmaker.publisher-control.articles.backoff", () -> "10ms");
    // Opens after two failed calls of the last two, and stays open for the whole test.
    registry.add(
        "rainmaker.publisher-control.articles.reader.circuit-breaker.window-size", () -> 2);
    registry.add(
        "rainmaker.publisher-control.articles.reader.circuit-breaker.minimum-calls", () -> 2);
    registry.add(
        "rainmaker.publisher-control.articles.reader.circuit-breaker.open-for", () -> "10m");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ArticleSync sync;
  @Autowired private SuggestionDecisions decisions;

  @Test
  void openCircuitNeverGivesLinksAlsoForArticleThatHadThem() throws Exception {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    String green = article(article, 1, "green", suggestion(home, HOME_OFFER, 1));
    FakeG2Articles.g2Sends(g2, sync, green);
    decisions.approve(home, "demo-editor");
    g2Answers(article, okJson("{\"articles\": [" + green + "]}"));
    MockHttpServletResponse before = request(article);
    assertThat(before.getStatus()).isEqualTo(200);
    assertThat(before.getContentAsString(StandardCharsets.UTF_8)).contains(HOME_OFFER.toString());

    g2Answers(article, aResponse().withStatus(503));
    assertThat(request(article).getStatus()).isEqualTo(503);
    assertThat(request(article).getStatus()).isEqualTo(503);
    g2.resetRequests();
    // G2 is up again, but the circuit is open: G2 isn't asked, and the answer is still 503.
    g2Answers(article, okJson("{\"articles\": [" + green + "]}"));

    MockHttpServletResponse whileOpen = request(article);

    assertThat(whileOpen.getStatus()).isEqualTo(503);
    assertThat(whileOpen.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("links");
    g2.verify(
        0, getRequestedFor(urlPathEqualTo(ARTICLES)).withQueryParam("url", equalTo(url(article))));
  }

  private static void g2Answers(UUID article, ResponseDefinitionBuilder answer) {
    g2.stubFor(
        WireMock.get(urlPathEqualTo(ARTICLES))
            .withQueryParam("url", equalTo(url(article)))
            .willReturn(answer));
  }

  private static String url(UUID article) {
    return "http://localhost:3000/artikel/" + article;
  }

  private MockHttpServletResponse request(UUID article) throws Exception {
    return mockMvc.perform(get(API).param("url", url(article))).andReturn().getResponse();
  }
}
