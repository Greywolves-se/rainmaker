package se.rainmaker.contentingestion.page;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArticlePageParserTest {

  private static final String URL = "http://localhost:3000/artikel/badrum";

  /** The same markup as the test site's article.html: only the article is inside article. */
  static String page(String section, String paragraphs) {
    String template =
        """
        <!doctype html>
        <html lang="sv">
        <body>
          <nav><p>Meny</p></nav>
          <article class="article" data-article-id="vb-0006">
            <a class="article__section" href="/sektion/%s">Bostad</a>
            <h1 class="article__headline" data-rainmaker-headline>Badrummet</h1>
            %s
            <div class="paywall__offer"><strong>Bli PLUS-läsare</strong></div>
          </article>
          <aside><p>Läs också</p></aside>
        </body>
        </html>
        """;
    return template.formatted(section, paragraphs);
  }

  private final ArticlePageParser parser = new ArticlePageParser();

  @Test
  void readsOnlyTheParagraphsInsideTheArticle() {
    String html =
        page(
            "bostad",
            "<p class=\"article__lead\">Ingressen.</p><p>Första stycket.</p>"
                + "<blockquote><p>Ett citat.</p></blockquote>");

    ArticlePage article = parser.parse(html.getBytes(UTF_8), URL);

    assertThat(article.text()).isEqualTo("Ingressen.\n\nFörsta stycket.\n\nEtt citat.");
    assertThat(article.text()).doesNotContain("Meny", "Läs också", "Badrummet", "PLUS");
  }

  @Test
  void readsTheSectionIdFromTheSectionLink() {
    ArticlePage article = parser.parse(page("halsa", "<p>Text.</p>").getBytes(UTF_8), URL);

    assertThat(article.section()).isEqualTo("halsa");
  }

  @Test
  void refusesPageWithoutArticle() {
    byte[] html = "<html><body><p>Sidan finns inte</p></body></html>".getBytes(UTF_8);

    assertThatThrownBy(() -> parser.parse(html, URL))
        .isInstanceOf(InvalidArticlePageException.class)
        .hasMessageContaining(URL);
  }

  @Test
  void refusesArticleWithoutText() {
    assertThatThrownBy(() -> parser.parse(page("bostad", "").getBytes(UTF_8), URL))
        .isInstanceOf(InvalidArticlePageException.class)
        .hasMessageContaining("text");
  }

  @Test
  void refusesArticleWithoutSection() {
    byte[] html = "<html><body><article><p>Text.</p></article></body></html>".getBytes(UTF_8);

    assertThatThrownBy(() -> parser.parse(html, URL))
        .isInstanceOf(InvalidArticlePageException.class)
        .hasMessageContaining("section");
  }
}
