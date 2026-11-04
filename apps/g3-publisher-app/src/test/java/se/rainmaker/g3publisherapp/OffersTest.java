package se.rainmaker.g3publisherapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** S4's fictional offers, the offers API and the landing pages (#149, contract 3.4 and 7.2). */
@SpringBootTest(properties = "rainmaker.publisher-control.articles.polling=false")
@AutoConfigureMockMvc
class OffersTest {

  /**
   * The demo's offers per commercial category (#260): two or three for each green category, so the
   * editor sees a ranking. None for "health" and "personal-finance": those articles are yellow and
   * get no suggestions (contract 3.3, D-4). "other" never has an offer.
   */
  private static final Map<String, Long> OFFERS_PER_CATEGORY =
      Map.of(
          "travel", 3L,
          "outdoor", 3L,
          "sport", 3L,
          "culture", 2L,
          "food", 3L,
          "home", 3L,
          "cars", 2L);

  @DynamicPropertySource
  static void databases(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  @Test
  void offersApiListsTheFictionalOffers() throws Exception {
    List<JsonNode> offers = offers();

    assertThat(offers.stream().map(offer -> offer.get("offerId").asString()).distinct())
        .hasSameSizeAs(offers);
    for (JsonNode offer : offers) {
      assertThat(offer.get("advertiser").asString()).isNotBlank();
      assertThat(offer.get("title").asString()).isNotBlank();
      assertThat(offer.get("text").asString()).isNotBlank();
      assertThat(offer.get("cpcOre").asInt()).isPositive();
      assertThat(offer.get("destinationUrl").asString())
          .isEqualTo("http://localhost:8083/landing/" + offer.get("offerId").asString());
    }
  }

  @Test
  void everyGreenCategoryHasTwoOrThreeOffersAndSensitiveOnesNone() throws Exception {
    Map<String, Long> perCategory =
        offers().stream()
            .collect(
                Collectors.groupingBy(
                    offer -> offer.get("category").asString(), Collectors.counting()));

    assertThat(perCategory).isEqualTo(OFFERS_PER_CATEGORY);
  }

  /** Offers in one category fit an article equally well, so CPC orders them (contract 4). */
  @Test
  void offersInOneCategoryHaveDifferentPrices() throws Exception {
    Map<String, List<Integer>> pricesPerCategory =
        offers().stream()
            .collect(
                Collectors.groupingBy(
                    offer -> offer.get("category").asString(),
                    Collectors.mapping(offer -> offer.get("cpcOre").asInt(), Collectors.toList())));

    pricesPerCategory.forEach(
        (category, prices) -> assertThat(prices).as(category).doesNotHaveDuplicates());
  }

  @Test
  void everyOffersDestinationOpensItsLandingPage() throws Exception {
    for (JsonNode offer : offers()) {
      String path = URI.create(offer.get("destinationUrl").asString()).getPath();

      MockHttpServletResponse page = mockMvc.perform(get(path)).andReturn().getResponse();

      assertThat(page.getStatus()).as(path).isEqualTo(200);
      assertThat(page.getContentType()).isEqualTo("text/html;charset=UTF-8");
      assertThat(page.getContentAsString(StandardCharsets.UTF_8))
          .contains(escaped(offer.get("title").asString()))
          .contains(escaped(offer.get("advertiser").asString()))
          .contains("Fiktivt testinnehåll");
    }
  }

  @Test
  void landingPageIsInEnglishWhenTheBrowserAsksForIt() throws Exception {
    String path = URI.create(offers().get(0).get("destinationUrl").asString()).getPath();

    String page =
        mockMvc
            .perform(get(path).header("Accept-Language", "en"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(page).contains("Fictional test content").doesNotContain("Fiktivt testinnehåll");
  }

  @Test
  void unknownOfferHasNoLandingPage() throws Exception {
    assertThat(
            mockMvc
                .perform(get("/landing/" + UUID.randomUUID()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);
  }

  @Test
  void malformedOfferIdIsBadRequest() throws Exception {
    assertThat(mockMvc.perform(get("/landing/abc")).andReturn().getResponse().getStatus())
        .isEqualTo(400);
  }

  private List<JsonNode> offers() throws Exception {
    MockHttpServletResponse response =
        mockMvc.perform(get("/api/offers")).andReturn().getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    return jsonMapper
        .readTree(response.getContentAsString(StandardCharsets.UTF_8))
        .get("offers")
        .valueStream()
        .toList();
  }

  private static String escaped(String text) {
    return HtmlUtils.htmlEscape(text, "UTF-8");
  }
}
