package se.rainmaker.testsite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static se.rainmaker.testsite.web.WebTestSupport.escaped;
import static se.rainmaker.testsite.web.WebTestSupport.fetch;
import static se.rainmaker.testsite.web.WebTestSupport.page;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Articles published and edited while the site runs (#159). The site reads a copy of content/, so
 * the tests can change files without touching the real content.
 */
@SpringBootTest(properties = WebTestSupport.BASE_URL_PROPERTY)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LiveArticlesTest {

  private static final String ATOM = "http://www.w3.org/2005/Atom";
  private static final String TEMPLATE = "vb-0006-badrumsrenovering.json";
  private static final Path CONTENT = copyOfContent();

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;

  @DynamicPropertySource
  static void contentDirectory(DynamicPropertyRegistry registry) {
    registry.add("site.content-directory", CONTENT::toString);
  }

  @AfterAll
  static void deleteCopy() throws IOException {
    FileSystemUtils.deleteRecursively(CONTENT);
  }

  @Test
  void newArticleShowsOnItsPageInTheFeedAndInTheSitemapWithoutRestart() throws Exception {
    writeArticle("vb-9001", "nytt-fran-vintra", "Nytt från Vintra", "Första stycket.", null);

    assertThat(page(mockMvc, "/artikel/nytt-fran-vintra")).contains(escaped("Nytt från Vintra"));
    assertThat(rssItems("https://test.example/artikel/nytt-fran-vintra")).hasSize(1);
    assertThat(text("/sitemap.xml"))
        .contains("<loc>https://test.example/artikel/nytt-fran-vintra</loc>");
  }

  @Test
  void editedArticleKeepsItsUrlGetsNewUpdateTimeInTheFeedAndShowsTheNewText() throws Exception {
    writeArticle("vb-9002", "badet-i-vintra", "Badet öppnar igen", "Simhallen öppnar.", null);
    assertThat(page(mockMvc, "/artikel/badet-i-vintra")).contains(escaped("Badet öppnar igen"));

    writeArticle(
        "vb-9002",
        "badet-i-vintra",
        "Badet stängt efter olycka",
        "En olycka i simhallen.",
        "2026-10-30T09:40:00+01:00");

    assertThat(page(mockMvc, "/artikel/badet-i-vintra"))
        .contains(escaped("Badet stängt efter olycka"))
        .contains(escaped("En olycka i simhallen."))
        .doesNotContain(escaped("Badet öppnar igen"));
    List<Element> items = rssItems("https://test.example/artikel/badet-i-vintra");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getElementsByTagNameNS(ATOM, "updated").item(0).getTextContent())
        .isEqualTo("2026-10-30T09:40:00+01:00");
    assertThat(text("/sitemap.xml")).contains("<lastmod>2026-10-30T09:40:00+01:00</lastmod>");
  }

  @Test
  void invalidContentIsRefusedAndTheSiteKeepsServingTheLastGoodVersion(CapturedOutput output)
      throws Exception {
    String feedBefore = text("/rss.xml");
    Path broken = CONTENT.resolve("articles").resolve("vb-9003-trasig.json");
    ObjectNode article = article("vb-9003", "trasig", "Trasig", "Text.", null);
    article.put("section", "okand");
    Files.writeString(broken, jsonMapper.writeValueAsString(article));
    try {
      assertThat(text("/rss.xml")).isEqualTo(feedBefore);
      assertThat(fetch(mockMvc, "/artikel/trasig").getResponse().getStatus()).isEqualTo(404);
      assertThat(
              fetch(mockMvc, "/artikel/badrumsrenovering-experternas-rad")
                  .getResponse()
                  .getStatus())
          .isEqualTo(200);
      // The log line names the file and the problem (JSON logs escape the quotes around the field).
      assertThat(output).contains("vb-9003-trasig.json").contains("is not a known section: okand");
    } finally {
      Files.delete(broken);
    }
  }

  /** Writes an article file, built from a real article with a few fields changed. */
  private void writeArticle(
      String id, String slug, String headline, String paragraph, String updatedAt)
      throws IOException {
    Path file = CONTENT.resolve("articles").resolve(id + ".json");
    Files.writeString(
        file, jsonMapper.writeValueAsString(article(id, slug, headline, paragraph, updatedAt)));
  }

  private ObjectNode article(
      String id, String slug, String headline, String paragraph, String updatedAt)
      throws IOException {
    ObjectNode article =
        (ObjectNode)
            jsonMapper.readTree(Files.readString(CONTENT.resolve("articles").resolve(TEMPLATE)));
    article.put("id", id).put("slug", slug).put("headline", headline).put("updatedAt", updatedAt);
    article.putArray("body").addObject().put("type", "paragraph").put("text", paragraph);
    return article;
  }

  private String text(String path) throws Exception {
    return fetch(mockMvc, path).getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  /** The RSS items whose link is {@code link}. */
  private List<Element> rssItems(String link) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document feed =
        factory
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(text("/rss.xml").getBytes(StandardCharsets.UTF_8)));
    NodeList items = feed.getElementsByTagName("item");
    List<Element> matching = new ArrayList<>();
    for (int index = 0; index < items.getLength(); index++) {
      Element item = (Element) items.item(index);
      if (link.equals(item.getElementsByTagName("link").item(0).getTextContent())) {
        matching.add(item);
      }
    }
    return matching;
  }

  private static Path copyOfContent() {
    try {
      Path copy = Files.createTempDirectory("test-site-content");
      FileSystemUtils.copyRecursively(Path.of("content"), copy);
      return copy;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
