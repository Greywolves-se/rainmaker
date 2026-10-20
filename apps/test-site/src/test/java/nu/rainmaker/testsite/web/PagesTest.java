package nu.rainmaker.testsite.web;

import static nu.rainmaker.testsite.web.WebTestSupport.articleElement;
import static nu.rainmaker.testsite.web.WebTestSupport.body;
import static nu.rainmaker.testsite.web.WebTestSupport.escaped;
import static nu.rainmaker.testsite.web.WebTestSupport.fetch;
import static nu.rainmaker.testsite.web.WebTestSupport.page;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import nu.rainmaker.testsite.content.AdCreative;
import nu.rainmaker.testsite.content.AdInventory;
import nu.rainmaker.testsite.content.Article;
import nu.rainmaker.testsite.content.ArticleBlock;
import nu.rainmaker.testsite.content.ArticleRepository;
import nu.rainmaker.testsite.content.FrontPage;
import nu.rainmaker.testsite.content.Section;
import nu.rainmaker.testsite.view.ViewHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The HTML pages, rendered by the real application with the real content files. */
@SpringBootTest(properties = WebTestSupport.BASE_URL_PROPERTY)
@AutoConfigureMockMvc
class PagesTest {

  private static final Locale SWEDISH = Locale.forLanguageTag("sv");

  @Autowired private MockMvc mockMvc;
  @Autowired private ArticleRepository articles;
  @Autowired private FrontPage frontPage;
  @Autowired private AdInventory adInventory;
  @Autowired private MessageSource messages;
  @Autowired private ViewHelper view;

  private String message(String key, Object... arguments) {
    return messages.getMessage(key, arguments, SWEDISH);
  }

  /** Every HTML page on the site, including a 404. */
  static Stream<String> allPages() {
    return Stream.of(
        "/",
        "/artikel/mallorca-hosten-okanda-parlor",
        "/artikel/test-familjeelbilen-nordvik-e5",
        "/artikel/tva-doda-i-trafikolycka-utanfor-vintra",
        "/sektion/sport",
        "/sektion/nyheter",
        "/finns-inte");
  }

  @ParameterizedTest
  @MethodSource("allPages")
  void everyPageIsLabelledAsFictionAndKeptOutOfSearchEngines(String path) throws Exception {
    String html = page(mockMvc, path);

    assertThat(html)
        .startsWith("<!DOCTYPE html>")
        .contains("<html lang=\"sv\">")
        .contains(escaped(message("fiction.label")))
        .contains("<meta name=\"robots\" content=\"noindex\">");
  }

  @Test
  void sendsSecurityHeaders() throws Exception {
    MvcResult result = fetch(mockMvc, "/");

    assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(result.getResponse().getHeader("X-Robots-Tag")).isEqualTo("noindex");
    assertThat(result.getResponse().getHeader("Content-Security-Policy"))
        .startsWith("default-src 'self'")
        .contains("script-src 'self';")
        .contains("frame-ancestors 'none'");
  }

  // ---------------------------------------------------------------- front page

  @Test
  void frontPageLinksToEveryArticle() throws Exception {
    String html = page(mockMvc, "/");

    for (Article article : articles.all()) {
      assertThat(html).contains("href=\"" + article.path() + "\"");
    }
  }

  @Test
  void frontPageUsesTheEditorialLeadAndBreakingNews() throws Exception {
    String html = page(mockMvc, "/");
    int leadStart = html.indexOf("card--lead");
    String lead = html.substring(leadStart, html.indexOf("</article>", leadStart));

    assertThat(lead).contains(escaped(frontPage.lead().headline()));
    assertThat(html)
        .contains("<a class=\"breaking\" href=\"" + frontPage.breaking().path() + "\">");
    // The lead story is not repeated in the story grid.
    assertThat(html.split("class=\"card__link\" href=\"" + frontPage.lead().path() + "\""))
        .hasSize(2);
  }

  @Test
  void frontPageListsMostReadInTheConfiguredOrder() throws Exception {
    String html = page(mockMvc, "/");
    String list = html.substring(html.indexOf("class=\"most-read\""));

    List<Integer> positions = new ArrayList<>();
    for (Article article : frontPage.mostRead()) {
      positions.add(list.indexOf("href=\"" + article.path() + "\""));
    }
    assertThat(positions).doesNotContain(-1).isSorted();
  }

  // ---------------------------------------------------------------- articles

  static Stream<String> allSlugs() {
    return Stream.of(
        "mallorca-hosten-okanda-parlor",
        "vintra-if-klart-for-superettan",
        "man-gripen-efter-ranforsok-i-centrala-vintra",
        "vintra-stadsteater-satsar-pa-musikal",
        "kantarellerna-har-exploderat-sa-hittar-du-dem",
        "badrumsrenovering-experternas-rad",
        "test-familjeelbilen-nordvik-e5",
        "allt-fler-unga-sover-for-lite",
        "var-fjarde-saknar-buffert",
        "tva-doda-i-trafikolycka-utanfor-vintra",
        "fjallvandring-i-oktober-guide",
        "vintraloppet-firar-25-ar");
  }

  @Test
  void slugListCoversEveryArticle() {
    assertThat(allSlugs())
        .containsExactlyInAnyOrderElementsOf(articles.all().stream().map(Article::slug).toList());
  }

  @ParameterizedTest
  @MethodSource("allSlugs")
  void articlePageHasMarkupForRainmaker(String slug) throws Exception {
    Article article = articles.findBySlug(slug).orElseThrow();
    String element = articleElement(page(mockMvc, article.path()));

    assertThat(element)
        .contains("data-article-id=\"" + article.id() + "\"")
        .contains(
            "<h1 class=\"article__headline\" data-rainmaker-headline>"
                + escaped(article.headline())
                + "</h1>");
    // Content Ingestion (S5) must find every paragraph inside <article>.
    for (ArticleBlock block : article.body()) {
      if (block instanceof ArticleBlock.Paragraph paragraph) {
        assertThat(element).contains("<p>" + escaped(paragraph.text()) + "</p>");
      }
    }
  }

  @ParameterizedTest
  @MethodSource("allSlugs")
  void articlePageNeverRevealsTheExpectedZone(String slug) throws Exception {
    String html = page(mockMvc, "/artikel/" + slug);

    assertThat(html).doesNotContain("expectedZone").doesNotContain("data-zone");
    assertThat(articleElement(html)).doesNotContainPattern("\\b(green|yellow|red)\\b");
  }

  @ParameterizedTest
  @MethodSource("allSlugs")
  void articlePageKeepsBannerMenusAndAdsTextOutsideTheArticleText(String slug) throws Exception {
    String element = articleElement(page(mockMvc, "/artikel/" + slug));

    assertThat(element)
        .doesNotContain(escaped(message("fiction.label")))
        .doesNotContain("section-nav");
    // The in-article ad slot adds no <p>, so it never mixes with the article text.
    Matcher slot = Pattern.compile("<aside class=\"ad-slot.*?</aside>").matcher(element);
    while (slot.find()) {
      assertThat(slot.group()).doesNotContain("<p");
    }
  }

  @ParameterizedTest
  @MethodSource("allSlugs")
  void articlePageShowsTheReadingTime(String slug) throws Exception {
    Article article = articles.findBySlug(slug).orElseThrow();

    assertThat(page(mockMvc, article.path()))
        .contains(escaped(message("article.readingTime", view.readingTimeMinutes(article))));
  }

  @Test
  void plusArticlesAreLockedButKeepTheFullText() throws Exception {
    List<Article> premium = articles.all().stream().filter(Article::premium).toList();
    assertThat(premium).isNotEmpty();

    for (Article article : premium) {
      String element = articleElement(page(mockMvc, article.path()));
      assertThat(element)
          .contains("data-paywall-state=\"locked\"")
          .contains("data-paywall-unlock")
          .contains("class=\"plus-badge\"");
      String offer = element.substring(element.indexOf("class=\"paywall__offer\""));
      assertThat(offer).doesNotContain("<p");
    }
  }

  @Test
  void freeArticlesHaveNoPaywall() throws Exception {
    for (Article article : articles.all()) {
      if (!article.premium()) {
        assertThat(page(mockMvc, article.path())).doesNotContain("data-paywall-state");
      }
    }
  }

  // ---------------------------------------------------------------- ads

  @Test
  void adSlotsOnlyReferToExistingCreatives() throws Exception {
    List<String> ids = adInventory.creatives().stream().map(AdCreative::id).toList();
    Pattern creativeId = Pattern.compile("data-ad-creative-id=\"([^\"]+)\"");

    for (String path :
        Stream.concat(Stream.of("/"), allSlugs().map(s -> "/artikel/" + s)).toList()) {
      Matcher matcher = creativeId.matcher(page(mockMvc, path));
      int slots = 0;
      while (matcher.find()) {
        assertThat(ids).contains(matcher.group(1));
        slots++;
      }
      assertThat(slots).as(path).isGreaterThanOrEqualTo(2);
    }
  }

  @Test
  void adTextIsNeverInTheHtml() throws Exception {
    String html = page(mockMvc, "/");

    for (AdCreative creative : adInventory.creatives()) {
      assertThat(html).doesNotContain(escaped(creative.headline()));
    }
  }

  @Test
  void adsAreTargetedToTheSection() throws Exception {
    String html = page(mockMvc, "/artikel/test-familjeelbilen-nordvik-e5");

    assertThat(html)
        .contains("data-ad-slot=\"panorama\" data-ad-creative-id=\"vintra-bil-provkorning\"");
  }

  @Test
  void inArticleAdDoesNotReserveSpace() throws Exception {
    String element = articleElement(page(mockMvc, "/artikel/mallorca-hosten-okanda-parlor"));

    assertThat(element).containsPattern("data-ad-slot=\"rectangle\"[^>]*data-ad-reserve=\"false\"");
  }

  // ---------------------------------------------------------------- consent and scripts

  @Test
  void rendersTheConsentBannerHiddenWithBothChoices() throws Exception {
    String html = page(mockMvc, "/");

    assertThat(html)
        .contains("data-consent-banner hidden")
        .contains("data-consent-choice=\"all\"")
        .contains("data-consent-choice=\"necessary\"")
        .contains("data-consent-open");
  }

  @Test
  void loadsTheConsentStubFirstAndEverythingElseAsync() throws Exception {
    Matcher script = Pattern.compile("<script[^>]*>").matcher(page(mockMvc, "/"));
    List<String> scripts = new ArrayList<>();
    while (script.find()) {
      scripts.add(script.group());
    }

    assertThat(scripts.get(0)).isEqualTo("<script src=\"/static/js/tcf-stub.js\">");
    assertThat(scripts.subList(1, scripts.size()))
        .allMatch(tag -> tag.contains(" async") || tag.contains("type=\"module\""));
    assertThat(scripts).noneMatch(tag -> tag.contains("rainmaker"));
  }

  // ---------------------------------------------------------------- sections

  @Test
  void sectionPageListsOnlyItsOwnArticles() throws Exception {
    String html = page(mockMvc, "/sektion/resor");

    for (Article article : articles.all()) {
      boolean linked = html.contains("class=\"card__link\" href=\"" + article.path() + "\"");
      assertThat(linked).as(article.slug()).isEqualTo(article.section() == Section.RESOR);
    }
    assertThat(html).contains(message("sectionPage.articleCount.other", 2));
  }

  @Test
  void everySectionPageWorksAndHighlightsItsMenuItem() throws Exception {
    for (Section section : Section.values()) {
      MvcResult result = fetch(mockMvc, "/sektion/" + section.id());
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      assertThat(body(result))
          .contains("href=\"/sektion/" + section.id() + "\" aria-current=\"page\"");
    }
  }
}
