package nu.rainmaker.testsite.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Validation of article files: every broken field must stop the site with a clear message. */
class ArticleParserTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** A minimal valid article; each test changes one thing. */
  private static ObjectNode validArticle() {
    ObjectNode article = JSON.createObjectNode();
    article.put("id", "vb-9999");
    article.put("slug", "test-artikel");
    article.put("section", "sport");
    article.put("expectedZone", "green");
    article.put("kicker", "TEST");
    article.put("headline", "Rubrik");
    article.put("lead", "Ingress");
    article.putObject("author").put("name", "Test Testsson").put("role", "Reporter");
    article.put("publishedAt", "2026-10-13T09:00:00+02:00");
    article.putArray("body").addObject().put("type", "paragraph").put("text", "Text.");
    return article;
  }

  private static ObjectNode validArticleWith(Consumer<ObjectNode> change) {
    ObjectNode article = validArticle();
    change.accept(article);
    return article;
  }

  @Test
  void parsesValidArticleWithDefaults() {
    Article article = ArticleParser.parse(validArticle(), "test.json");

    assertThat(article.slug()).isEqualTo("test-artikel");
    assertThat(article.section()).isEqualTo(Section.SPORT);
    assertThat(article.expectedZone()).isEqualTo(BrandSafetyZone.GREEN);
    assertThat(article.premium()).isFalse();
    assertThat(article.lastUpdate()).isEmpty();
    assertThat(article.path()).isEqualTo("/artikel/test-artikel");
  }

  @Test
  void parsesAllBlockTypes() {
    ObjectNode json =
        validArticleWith(
            article -> {
              var body = article.putArray("body");
              body.addObject().put("type", "paragraph").put("text", "Stycke");
              body.addObject().put("type", "subheading").put("text", "Mellanrubrik");
              body.addObject().put("type", "quote").put("text", "Citat").put("source", "Någon");
              var factbox = body.addObject().put("type", "factbox").put("title", "Fakta");
              factbox.putArray("items").add("Ett").add("Två");
            });

    assertThat(ArticleParser.parse(json, "test.json").body())
        .extracting(ArticleBlock::type)
        .containsExactly("paragraph", "subheading", "quote", "factbox");
  }

  static Stream<Arguments> brokenArticles() {
    return Stream.of(
        Arguments.of("missing headline", validArticleWith(a -> a.remove("headline")), "headline"),
        Arguments.of("blank lead", validArticleWith(a -> a.put("lead", "  ")), "lead"),
        Arguments.of("unknown section", validArticleWith(a -> a.put("section", "x")), "section"),
        Arguments.of(
            "unknown zone", validArticleWith(a -> a.put("expectedZone", "orange")), "expectedZone"),
        Arguments.of("slug with capitals", validArticleWith(a -> a.put("slug", "Abc")), "slug"),
        Arguments.of(
            "date without time zone",
            validArticleWith(a -> a.put("publishedAt", "2026-10-13T09:00:00")),
            "publishedAt"),
        Arguments.of(
            "impossible date",
            validArticleWith(a -> a.put("publishedAt", "2026-13-45T09:00:00Z")),
            "publishedAt"),
        Arguments.of("premium as text", validArticleWith(a -> a.put("premium", "yes")), "premium"),
        Arguments.of("empty body", validArticleWith(a -> a.putArray("body")), "body"),
        Arguments.of(
            "body without paragraph",
            validArticleWith(
                a ->
                    a.putArray("body").addObject().put("type", "subheading").put("text", "Rubrik")),
            "paragraph"),
        Arguments.of(
            "unknown block type",
            validArticleWith(
                a -> a.putArray("body").addObject().put("type", "video").put("text", "x")),
            "body[0].type"),
        Arguments.of(
            "factbox without items",
            validArticleWith(
                a -> {
                  var body = a.putArray("body");
                  body.addObject().put("type", "paragraph").put("text", "x");
                  body.addObject().put("type", "factbox").put("title", "Fakta").putArray("items");
                }),
            "body[1].items"),
        Arguments.of(
            "author without name",
            validArticleWith(a -> a.putObject("author").put("role", "Reporter")),
            "author.name"));
  }

  @ParameterizedTest(name = "rejects {0}")
  @MethodSource("brokenArticles")
  void rejectsBrokenArticleAndNamesFileAndField(String description, ObjectNode json, String field) {
    assertThatThrownBy(() -> ArticleParser.parse(json, "broken.json"))
        .isInstanceOf(ContentValidationException.class)
        .hasMessageStartingWith("broken.json: ")
        .hasMessageContaining(field);
  }
}
