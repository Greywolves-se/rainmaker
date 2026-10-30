package se.rainmaker.widgetdelivery.links;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.rainmaker.trackingclick.api.ClickLinks;

/**
 * The widget API, {@code GET /widget/v1/links?url=…} (#151, contract 7.4): rainmaker.js gets the
 * article's approved links from G3, each with a signed click link instead of the offer's address.
 * Whatever G3 doesn't approve gets an empty list, and G3 being unavailable gets 503, never links.
 */
class WidgetLinksControllerTest {

  private static final String API = "/widget/v1/links";
  private static final String G3_API = "/api/approved-links";
  private static final String ARTICLE =
      "http://localhost:3000/artikel/badrumsrenovering-experternas-rad";

  /** G3's answer and G1's, exactly as contract 7.3 and 7.4 show them. */
  private static final String G3_EXAMPLE = ContractExamples.read("7.3-approved-links.json");

  private static final String WIDGET_EXAMPLE = ContractExamples.read("7.4-widget-links.json");

  /** The ids, destination and click link in the two examples. */
  private static final UUID ARTICLE_ID = UUID.fromString("0192f3c2-5e1a-7b10-8a3c-4d5e6f708192");

  private static final UUID OFFER_ID = UUID.fromString("0192f3d0-2b4c-7d10-9e8f-a1b2c3d4e5f6");
  private static final String DESTINATION = "http://localhost:8083/landing/" + OFFER_ID;
  private static final String CLICK_URL =
      "http://localhost:8081/c/eyJjIjoiMDE5MmYzZTQifQ.c2lnbmF0dXJl";

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @RegisterExtension
  static WireMockExtension g3 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  /** By default, a click link that names its offer, so a test can see which link is which. */
  private final ClickLinks clickLinks = mock(ClickLinks.class);

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new WidgetLinksController(new WidgetLinksService(g3Client(), clickLinks)))
          .build();

  private static G3ApprovedLinksClient g3Client() {
    return new G3ApprovedLinksClient(
        new ApprovedLinksProperties(
            URI.create(g3.baseUrl()),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5), // generous: the first request also warms up WireMock
            1,
            Duration.ofMillis(10),
            new ApprovedLinksProperties.CircuitBreaker(10, 10, 50, Duration.ofMinutes(1))));
  }

  private void g3Answers(String body) {
    g3.stubFor(WireMock.get(urlPathEqualTo(G3_API)).willReturn(okJson(body)));
  }

  private void clickLinksNameTheirOffer() {
    when(clickLinks.create(any(), any(), any()))
        .thenAnswer(call -> "http://localhost:8081/c/" + call.getArgument(1));
  }

  @Test
  void answersTheApprovedLinksAsTheContractShowsThem() throws Exception {
    g3Answers(G3_EXAMPLE);
    when(clickLinks.create(ARTICLE_ID, OFFER_ID, DESTINATION)).thenReturn(CLICK_URL);

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json(WIDGET_EXAMPLE, JsonCompareMode.STRICT))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void neverSendsTheOfferAddressToTheBrowser() throws Exception {
    g3Answers(G3_EXAMPLE);
    clickLinksNameTheirOffer();

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(jsonPath("$.links[0].destinationUrl").doesNotExist())
        .andExpect(jsonPath("$.links[0].clickUrl").value("http://localhost:8081/c/" + OFFER_ID));
  }

  @Test
  void asksG3AboutTheArticleWithNewCorrelationId() throws Exception {
    g3Answers(G3_EXAMPLE);
    clickLinksNameTheirOffer();

    mockMvc.perform(get(API).param("url", ARTICLE)).andExpect(status().isOk());

    g3.verify(
        getRequestedFor(urlPathEqualTo(G3_API))
            .withQueryParam("url", equalTo(ARTICLE))
            .withHeader("X-Correlation-Id", matching(UUID_PATTERN)));
  }

  /** Not what contract 7.3 allows, but without the article's id no click link can be made. */
  @Test
  void linksWithoutArticleIdAreNotShown() throws Exception {
    g3Answers(G3_EXAMPLE.replace("\"" + ARTICLE_ID + "\"", "null"));

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links").isEmpty());
    verify(clickLinks, never()).create(any(), any(), any());
  }

  @Test
  void articleWithoutApprovedLinksGetsEmptyList() throws Exception {
    // How G3 answers for a red, yellow or unclassified article, or one with only rejected
    // suggestions (contract 7.3): G3 decides, G1 passes it on.
    g3Answers(
        """
        {"articleId": "%s", "articleUrl": "%s", "links": []}
        """
            .formatted(ARTICLE_ID, ARTICLE));

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.articleUrl").value(ARTICLE))
        .andExpect(jsonPath("$.links").isEmpty());
  }

  @Test
  void unknownArticleGetsEmptyList() throws Exception {
    g3Answers(
        """
        {"articleId": null, "articleUrl": "%s", "links": []}
        """
            .formatted(ARTICLE));

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links").isEmpty());
  }

  @Test
  void articleThatTurnsRedAfterApprovalGetsEmptyList() throws Exception {
    g3.stubFor(
        WireMock.get(urlPathEqualTo(G3_API))
            .inScenario("zone")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(G3_EXAMPLE))
            .willSetStateTo("red"));
    g3.stubFor(
        WireMock.get(urlPathEqualTo(G3_API))
            .inScenario("zone")
            .whenScenarioStateIs("red")
            .willReturn(
                okJson(
                    """
                    {"articleId": "%s", "articleUrl": "%s", "links": []}
                    """
                        .formatted(ARTICLE_ID, ARTICLE))));
    clickLinksNameTheirOffer();
    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(jsonPath("$.links.length()").value(1));

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links").isEmpty());
  }

  @Test
  void g3UnavailableMeansServiceUnavailableNeverLinks() throws Exception {
    g3.stubFor(WireMock.get(urlPathEqualTo(G3_API)).willReturn(aResponse().withStatus(503)));

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().string(""));
    verify(clickLinks, never()).create(any(), any(), any());
  }

  @Test
  void givesAtMostThreeLinksInRankOrder() throws Exception {
    g3Answers(
        """
        {"articleId": "%s", "articleUrl": "%s", "links": [%s, %s, %s, %s]}
        """
            .formatted(ARTICLE_ID, ARTICLE, g3Link(4), g3Link(2), g3Link(1), g3Link(3)));
    clickLinksNameTheirOffer();

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(jsonPath("$.links.length()").value(3))
        .andExpect(jsonPath("$.links[0].offerId").value(offerId(1).toString()))
        .andExpect(jsonPath("$.links[1].offerId").value(offerId(2).toString()))
        .andExpect(jsonPath("$.links[2].offerId").value(offerId(3).toString()));
  }

  @Test
  void leavesOutLinkThatCannotBeSigned() throws Exception {
    g3Answers(
        """
        {"articleId": "%s", "articleUrl": "%s", "links": [%s, %s]}
        """
            .formatted(ARTICLE_ID, ARTICLE, g3Link(1), g3Link(2)));
    when(clickLinks.create(any(), any(), any()))
        .thenThrow(new IllegalArgumentException("not an http(s) URI"))
        .thenAnswer(call -> "http://localhost:8081/c/" + call.getArgument(1));

    mockMvc
        .perform(get(API).param("url", ARTICLE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links.length()").value(1))
        .andExpect(jsonPath("$.links[0].offerId").value(offerId(2).toString()));
  }

  @Test
  void refusesRequestWithoutArticleAddress() throws Exception {
    mockMvc.perform(get(API)).andExpect(status().isBadRequest());

    g3.verify(0, getRequestedFor(urlPathEqualTo(G3_API)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "/artikel/badrumsrenovering-experternas-rad",
        "ftp://localhost:3000/artikel/x",
        "javascript:alert(1)",
        "http://",
        "not an address"
      })
  void refusesAddressThatIsNotAbsoluteHttp(String url) throws Exception {
    mockMvc.perform(get(API).param("url", url)).andExpect(status().isBadRequest());

    g3.verify(0, getRequestedFor(urlPathEqualTo(G3_API)));
  }

  /** An approved link from G3 for the offer with this rank. */
  private static String g3Link(int rank) {
    String link =
        """
        {"offerId": "%s", "rank": %d, "advertiser": "Annonsör %d", "title": "Erbjudande %d",
         "text": "Text %d", "destinationUrl": "http://localhost:8083/landing/%d"}
        """;
    return link.formatted(offerId(rank), rank, rank, rank, rank, rank);
  }

  private static UUID offerId(int rank) {
    return UUID.fromString("0192f3d0-0000-7000-8000-00000000000" + rank);
  }
}
