package se.rainmaker.testsite.view;

import java.util.List;
import se.rainmaker.testsite.content.ArticleBlock;

/**
 * The article body as the template shows it: text blocks with an ad slot in between, split into a
 * free part and - for PLUS articles - a part behind the paywall.
 *
 * @param free shown to everyone
 * @param locked behind the paywall; empty for free articles
 */
public record ArticleBody(List<Item> free, List<Item> locked) {

  /** Makes the lists unmodifiable. */
  public ArticleBody {
    free = List.copyOf(free);
    locked = List.copyOf(locked);
  }

  /** True when part of the text is behind the paywall. */
  public boolean paywalled() {
    return !locked.isEmpty();
  }

  /**
   * One item in the body: either a text block or an ad slot.
   *
   * @param block the text block, or null for an ad
   * @param ad the ad slot, or null for a text block
   */
  public record Item(ArticleBlock block, AdSlot ad) {

    /** Creates a text item. */
    public static Item of(ArticleBlock block) {
      return new Item(block, null);
    }

    /** Creates an ad item. */
    public static Item of(AdSlot ad) {
      return new Item(null, ad);
    }

    /** True for an ad slot. */
    public boolean isAd() {
      return ad != null;
    }
  }
}
