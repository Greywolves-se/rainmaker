package nu.rainmaker.testsite.web;

import static nu.rainmaker.testsite.web.WebTestSupport.fetch;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import nu.rainmaker.testsite.content.Article;
import nu.rainmaker.testsite.content.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** RSS, sitemap, robots.txt, evaluation labels and the JSON endpoints. */
@SpringBootTest(properties = WebTestSupport.BASE_URL_PROPERTY)
@AutoConfigureMockMvc
class FeedsTest {

  private static final String NEWS = "http://www.google.com/schemas/sitemap-news/0.9";

  @Autowired private MockMvc mockMvc;
  @Autowired private ArticleRepository articles;
  @Autowired private ObjectMapper objectMapper;

  private String text(String path) throws Exception {
    return fetch(mockMvc, path).getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  /** Parses the response as XML - this fails if the XML is not well-formed. */
  private Document xml(String path) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    byte[] bytes = text(path).getBytes(StandardCharsets.UTF_8);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  @Test
  void rssFeedIsValidAndListsEveryArticle() throws Exception {
    MvcResult result = fetch(mockMvc, "/rss.xml");
    assertThat(result.getResponse().getContentType())
        .isEqualTo("application/rss+xml;charset=UTF-8");

    Document feed = xml("/rss.xml");
    NodeList items = feed.getElementsByTagName("item");
    assertThat(items.getLength()).isEqualTo(articles.all().size());

    for (int index = 0; index < items.getLength(); index++) {
      Element item = (Element) items.item(index);
      Article article = articles.all().get(index);
      assertThat(child(item, "title")).isEqualTo(article.headline());
      assertThat(child(item, "link")).isEqualTo("https://test.example" + article.path());
      assertThat(child(item, "guid")).isEqualTo(article.id());
      assertThat(child(item, "pubDate"))
          .matches("[A-Z][a-z]{2}, \\d{1,2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT");
    }
  }

  @Test
  void rssFeedIsTheSameOnEveryRequest() throws Exception {
    assertThat(text("/rss.xml")).isEqualTo(text("/rss.xml"));
  }

  @Test
  void newsSitemapIsValidAndListsEveryArticle() throws Exception {
    Document sitemap = xml("/sitemap.xml");

    assertThat(sitemap.getElementsByTagName("url").getLength()).isEqualTo(articles.all().size());
    assertThat(sitemap.getElementsByTagNameNS(NEWS, "publication_date").getLength())
        .isEqualTo(articles.all().size());
    assertThat(text("/sitemap.xml"))
        .contains("<loc>https://test.example/artikel/mallorca-hosten-okanda-parlor</loc>");
  }

  @Test
  void robotsTxtPointsToTheSitemap() throws Exception {
    assertThat(text("/robots.txt")).contains("Sitemap: https://test.example/sitemap.xml");
  }

  @Test
  void evaluationLabelsHaveOneRowPerArticle() throws Exception {
    List<String> lines = text("/eval/labels.csv").lines().toList();

    assertThat(lines.get(0)).isEqualTo("article_id,expected_zone,section,url");
    assertThat(lines).hasSize(articles.all().size() + 1);
    for (Article article : articles.all()) {
      assertThat(lines)
          .contains(
              String.join(
                  ",",
                  article.id(),
                  article.expectedZone().id(),
                  article.section().id(),
                  "https://test.example" + article.path()));
    }
  }

  @Test
  void adCreativesAreServedAsJsonWithLowerCaseSections() throws Exception {
    JsonNode json = objectMapper.readTree(text("/ads/creatives.json"));

    assertThat(json.path("creatives").isArray()).isTrue();
    assertThat(json.path("creatives").get(0).path("id").asText()).isEqualTo("nordsol-mallorca");
    assertThat(json.path("creatives").get(0).path("sections").get(0).asText()).isEqualTo("resor");
  }

  @Test
  void tagContainerHasOnlyTheConsentGatedStatsTagByDefault() throws Exception {
    JsonNode tags = objectMapper.readTree(text("/tag-manager/container.json")).path("tags");

    assertThat(tags).hasSize(1);
    assertThat(tags.get(0).path("name").asText()).isEqualTo("vintra-stats");
    assertThat(tags.get(0).path("trigger").asText()).isEqualTo("consent");
  }

  @Test
  void healthCheckSaysOk() throws Exception {
    assertThat(text("/health")).isEqualTo("ok");
  }

  private static String child(Element parent, String name) {
    return parent.getElementsByTagName(name).item(0).getTextContent();
  }
}
