package nu.rainmaker.testsite.web;

import java.util.List;
import java.util.Locale;
import nu.rainmaker.testsite.content.Article;
import nu.rainmaker.testsite.content.ArticleRepository;
import nu.rainmaker.testsite.content.FrontPage;
import nu.rainmaker.testsite.content.Section;
import nu.rainmaker.testsite.view.AdSlot;
import nu.rainmaker.testsite.view.AdSlots;
import nu.rainmaker.testsite.view.ArticleBodyBuilder;
import nu.rainmaker.testsite.view.ViewHelper;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The HTML pages.
 *
 * <ul>
 *   <li>{@code /} - front page: "JUST NU", top stories, latest, most read, more news, ads
 *   <li>{@code /artikel/{slug}} - one article (with paywall if PLUS)
 *   <li>{@code /sektion/{id}} - all articles in a section
 * </ul>
 *
 * <p>The HTML itself is in src/main/resources/templates; this class only collects the data.
 */
@Controller
public class PageController {

  /** How many stories share the top of the front page with the lead story. */
  static final int SECONDARY_STORY_COUNT = 2;

  static final int LATEST_NEWS_COUNT = 6;
  static final int RELATED_ARTICLE_COUNT = 4;

  private final ArticleRepository articles;
  private final FrontPage frontPage;
  private final AdSlots adSlots;
  private final ArticleBodyBuilder articleBodyBuilder;
  private final ViewHelper viewHelper;
  private final MessageSource messages;

  /** Creates the controller. */
  public PageController(
      ArticleRepository articles,
      FrontPage frontPage,
      AdSlots adSlots,
      ArticleBodyBuilder articleBodyBuilder,
      ViewHelper viewHelper,
      MessageSource messages) {
    this.articles = articles;
    this.frontPage = frontPage;
    this.adSlots = adSlots;
    this.articleBodyBuilder = articleBodyBuilder;
    this.viewHelper = viewHelper;
    this.messages = messages;
  }

  /** The front page. */
  @GetMapping("/")
  public String frontPage(Model model, Locale locale) {
    List<Article> others =
        articles.all().stream()
            .filter(article -> !article.id().equals(frontPage.lead().id()))
            .toList();
    int secondaryCount = Math.min(SECONDARY_STORY_COUNT, others.size());

    model.addAttribute("pageTitle", null);
    model.addAttribute("description", messages.getMessage("site.description", null, locale));
    model.addAttribute("path", "/");
    model.addAttribute("activeNav", "home");
    model.addAttribute("frontPage", frontPage);
    model.addAttribute("secondaryStories", others.subList(0, secondaryCount));
    model.addAttribute("moreStories", others.subList(secondaryCount, others.size()));
    model.addAttribute("latest", articles.all().stream().limit(LATEST_NEWS_COUNT).toList());
    model.addAttribute("panoramaAd", adSlots.slot(AdSlot.Format.PANORAMA, null, 0, true));
    model.addAttribute("skyscraperAd", adSlots.slot(AdSlot.Format.SKYSCRAPER, null, 1, true));
    return "front";
  }

  /** One article. */
  @GetMapping("/artikel/{slug}")
  public String article(@PathVariable String slug, Model model, Locale locale) {
    Article article =
        articles.findBySlug(slug).orElseThrow(() -> new PageNotFoundException("article " + slug));
    String sectionName = messages.getMessage(article.section().messageKey(), null, locale);

    model.addAttribute("pageTitle", article.headline());
    model.addAttribute("description", article.lead());
    model.addAttribute("path", article.path());
    model.addAttribute("activeNav", article.section().id());
    model.addAttribute("article", article);
    model.addAttribute("sectionName", sectionName);
    model.addAttribute("showKicker", viewHelper.showKicker(article, sectionName));
    model.addAttribute("body", articleBodyBuilder.build(article));
    model.addAttribute("related", articles.related(article, RELATED_ARTICLE_COUNT));
    model.addAttribute(
        "panoramaAd", adSlots.slot(AdSlot.Format.PANORAMA, article.section(), 0, true));
    model.addAttribute(
        "skyscraperAd", adSlots.slot(AdSlot.Format.SKYSCRAPER, article.section(), 2, true));
    return "article";
  }

  /** All articles in one section. */
  @GetMapping("/sektion/{sectionId}")
  public String section(@PathVariable String sectionId, Model model, Locale locale) {
    Section section =
        Section.fromId(sectionId)
            .orElseThrow(() -> new PageNotFoundException("section " + sectionId));
    String sectionName = messages.getMessage(section.messageKey(), null, locale);
    String tagline = messages.getMessage("site.tagline", null, locale);

    model.addAttribute("pageTitle", sectionName);
    model.addAttribute("description", sectionName + " – " + tagline);
    model.addAttribute("path", "/sektion/" + section.id());
    model.addAttribute("activeNav", section.id());
    model.addAttribute("section", section);
    model.addAttribute("sectionName", sectionName);
    model.addAttribute("sectionArticles", articles.inSection(section));
    model.addAttribute("panoramaAd", adSlots.slot(AdSlot.Format.PANORAMA, section, 0, true));
    return "section";
  }
}
