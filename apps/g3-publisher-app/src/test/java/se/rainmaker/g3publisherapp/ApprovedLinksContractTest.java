package se.rainmaker.g3publisherapp;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.rainmaker.g3publisherapp.FakeG2Articles.ARTICLES;
import static se.rainmaker.g3publisherapp.FakeG2Articles.article;
import static se.rainmaker.g3publisherapp.FakeG2Articles.suggestion;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import se.rainmaker.advertisermarketplace.api.Offer;
import se.rainmaker.advertisermarketplace.api.OfferCatalog;
import se.rainmaker.publishercontrol.articles.ArticleSync;
import se.rainmaker.publishercontrol.decision.SuggestionDecisions;

/**
 * G3's side of contract 7.3: for the example's article, the approved links API answers exactly
 * {@code docs/rainmaker/contract/7.3-approved-links.json}, the file G1's tests read too, so neither
 * side can rename or add a field alone. The example's offer isn't one of S4's fictional offers, so
 * the catalog is a mock here; {@link ApprovedLinksTest} uses the real one.
 */
@SpringBootTest(properties = "rainmaker.publisher-control.articles.polling=false")
@AutoConfigureMockMvc
class ApprovedLinksContractTest {

  private static final String EXAMPLE = ContractExamples.read("7.3-approved-links.json");
  private static final UUID ARTICLE = UUID.fromString("0192f3c2-5e1a-7b10-8a3c-4d5e6f708192");
  private static final String URL =
      "http://localhost:3000/artikel/badrumsrenovering-experternas-rad";
  private static final UUID OFFER = UUID.fromString("0192f3d0-2b4c-7d10-9e8f-a1b2c3d4e5f6");

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
  @Autowired private SuggestionDecisions decisions;
  @MockitoBean private OfferCatalog offers;

  @Test
  void answersTheExampleArticleExactlyAsTheContractShowsIt() throws Exception {
    when(offers.find(OFFER))
        .thenReturn(
            Optional.of(
                new Offer(
                    OFFER,
                    "Vintra Kakel & Bad",
                    "Gratis hembesök för badrumsoffert",
                    "Vi mäter upp och ger fast pris inom en vecka.",
                    "home",
                    "http://localhost:8083/landing/" + OFFER,
                    450)));
    UUID approved = UUID.randomUUID();
    String green =
        article(ARTICLE, 1, "green", suggestion(approved, OFFER, 1))
            .replace("http://localhost:3000/artikel/" + ARTICLE, URL);
    FakeG2Articles.g2Sends(g2, sync, green);
    decisions.approve(approved, "demo-editor");
    g2.stubFor(
        WireMock.get(urlPathEqualTo(ARTICLES))
            .withQueryParam("url", equalTo(URL))
            .willReturn(okJson("{\"articles\": [" + green + "]}")));

    mockMvc
        .perform(get("/api/approved-links").param("url", URL))
        .andExpect(status().isOk())
        .andExpect(content().json(EXAMPLE, JsonCompareMode.STRICT));
  }
}
