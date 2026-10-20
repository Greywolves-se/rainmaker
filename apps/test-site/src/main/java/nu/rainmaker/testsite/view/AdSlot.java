package nu.rainmaker.testsite.view;

import java.util.Locale;

/**
 * An empty ad slot on a page. The browser script static/js/ads.js fills it later.
 *
 * @param format panorama, rectangle or skyscraper
 * @param creativeId which ad the browser should put in the slot
 * @param reserveSpace whether the slot keeps its height while waiting. Well-behaved sites reserve
 *     space; many do not, and the page jumps when the ad arrives. One slot per article does NOT
 *     reserve space, on purpose, so Rainmaker can be tested for layout shift (CLS).
 */
public record AdSlot(Format format, String creativeId, boolean reserveSpace) {

  /** The common Swedish ad formats. */
  public enum Format {
    /** Wide banner above the content. */
    PANORAMA,
    /** Box inside the article text ("mobil/modul"). */
    RECTANGLE,
    /** Tall box in the right-hand column. */
    SKYSCRAPER;

    /** Lower-case name used in CSS classes and data attributes, e.g. "panorama". */
    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
