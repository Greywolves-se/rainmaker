package nu.rainmaker.testsite.content;

import static nu.rainmaker.testsite.content.ContentTestData.article;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The files front-page.json and ads.json: parsing, validation and the ad choice. */
class FrontPageAndAdsTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ArticleRepository articles =
      new ArticleRepository(
          List.of(
              article("vb-0001", Section.RESOR, "2026-10-13T08:00:00+02:00"),
              article("vb-0002", Section.SPORT, "2026-10-12T08:00:00+02:00")));

  private static JsonNode json(String text) throws Exception {
    return JSON.readTree(text.replace('\'', '"'));
  }

  @Test
  void parsesFrontPageWithoutBreakingNews() throws Exception {
    FrontPage page =
        FrontPage.parse(
            json("{'leadArticleId': 'vb-0002', 'mostReadArticleIds': ['vb-0001']}"), articles);

    assertThat(page.lead().id()).isEqualTo("vb-0002");
    assertThat(page.breakingNews()).isEmpty();
    assertThat(page.mostRead()).extracting(Article::id).containsExactly("vb-0001");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{'leadArticleId': 'vb-9999', 'mostReadArticleIds': ['vb-0001']}",
        "{'leadArticleId': 'vb-0001', 'breakingArticleId': 'x', 'mostReadArticleIds': ['vb-0001']}",
        "{'leadArticleId': 'vb-0001', 'mostReadArticleIds': []}",
        "{'leadArticleId': 'vb-0001', 'mostReadArticleIds': ['vb-0001', 'vb-0001']}",
        "{'leadArticleId': 1, 'mostReadArticleIds': ['vb-0001']}",
        "[]"
      })
  void rejectsBrokenFrontPage(String text) throws Exception {
    JsonNode broken = json(text);
    assertThatThrownBy(() -> FrontPage.parse(broken, articles))
        .isInstanceOf(ContentValidationException.class)
        .hasMessageStartingWith("front-page.json: ");
  }

  private static String creative(String id, String sections) {
    return "{'id': '"
        + id
        + "', 'advertiser': 'A', 'headline': 'H', 'text': 'T', 'callToAction': 'C',"
        + " 'theme': 'ocean', 'sections': ["
        + sections
        + "]}";
  }

  @Test
  void prefersAdsForTheSectionThenRotates() throws Exception {
    AdInventory inventory =
        AdInventory.parse(
            json(
                "{'creatives': ["
                    + creative("a", "'sport'")
                    + ","
                    + creative("b", "'resor'")
                    + ","
                    + creative("c", "")
                    + "]}"));

    assertThat(inventory.pick(Section.RESOR, 0).id()).isEqualTo("b");
    assertThat(inventory.pick(Section.RESOR, 1).id()).isEqualTo("a");
    assertThat(inventory.pick(null, 0).id()).isEqualTo("a");
    assertThat(inventory.pick(null, 4).id()).isEqualTo("b");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{'creatives': []}",
        "{'creatives': [{'id': 'a'}]}",
        "{'creatives': [AD_BAD_THEME]}",
        "{'creatives': [AD_BAD_SECTION]}",
        "{'creatives': [AD_BAD_ID]}",
        "{'creatives': [AD_OK, AD_OK]}"
      })
  void rejectsBrokenAds(String template) throws Exception {
    String text =
        template
            .replace("AD_BAD_THEME", creative("a", "").replace("ocean", "neon"))
            .replace("AD_BAD_SECTION", creative("a", "'sports'"))
            .replace("AD_BAD_ID", creative("a b", ""))
            .replace("AD_OK", creative("a", ""));
    JsonNode broken = json(text);
    assertThatThrownBy(() -> AdInventory.parse(broken))
        .isInstanceOf(ContentValidationException.class)
        .hasMessageStartingWith("ads.json: ");
  }
}
