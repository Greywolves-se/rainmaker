package nu.rainmaker.testsite.view;

import java.util.ArrayList;
import java.util.List;
import nu.rainmaker.testsite.content.Article;
import org.springframework.stereotype.Component;

/**
 * Places the in-article ad and the paywall in an article body.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>The ad goes after the first {@value #AD_AFTER_BLOCKS} blocks, if the article is long
 *       enough. It does not reserve space (see {@link AdSlot}).
 *   <li>PLUS articles show {@value #FREE_BLOCKS} blocks for free; the rest is behind the paywall.
 * </ul>
 */
@Component
public class ArticleBodyBuilder {

  /** The in-article ad is placed after this many blocks. */
  static final int AD_AFTER_BLOCKS = 3;

  /** PLUS articles show this many blocks before the paywall. */
  static final int FREE_BLOCKS = 4;

  /** The in-article ad is the second ad on the page (after the panorama). */
  private static final int IN_ARTICLE_SLOT_NUMBER = 1;

  private final AdSlots adSlots;

  /** Creates the builder. */
  public ArticleBodyBuilder(AdSlots adSlots) {
    this.adSlots = adSlots;
  }

  /** Builds the body for one article. */
  public ArticleBody build(Article article) {
    List<ArticleBody.Item> items = new ArrayList<>();
    article.body().forEach(block -> items.add(ArticleBody.Item.of(block)));

    boolean roomForAd = items.size() > AD_AFTER_BLOCKS + 1;
    if (roomForAd) {
      AdSlot ad =
          adSlots.slot(AdSlot.Format.RECTANGLE, article.section(), IN_ARTICLE_SLOT_NUMBER, false);
      items.add(AD_AFTER_BLOCKS, ArticleBody.Item.of(ad));
    }

    boolean paywalled = article.premium() && article.body().size() > FREE_BLOCKS;
    if (!paywalled) {
      return new ArticleBody(items, List.of());
    }
    // +1 because the ad (if any) was inserted before the paywall point.
    int split = FREE_BLOCKS + (roomForAd ? 1 : 0);
    return new ArticleBody(items.subList(0, split), items.subList(split, items.size()));
  }
}
