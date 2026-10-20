package nu.rainmaker.testsite.content;

import java.util.List;

/**
 * One fake ad from {@code content/ads.json}. All advertisers are invented.
 *
 * <p>The creative's text never goes into the page HTML: the browser fetches it later from
 * /ads/creatives.json, like real ads. Ad text is content (like articles), so it lives in the
 * content file and not in the translation file.
 *
 * @param id unique id, e.g. "nordsol-mallorca"
 * @param advertiser invented company name
 * @param headline big text in the ad
 * @param text smaller text
 * @param callToAction button text
 * @param theme colour theme, see {@link #THEMES}
 * @param sections sections where this ad is preferred (simple contextual targeting)
 */
public record AdCreative(
    String id,
    String advertiser,
    String headline,
    String text,
    String callToAction,
    String theme,
    List<Section> sections) {

  /** Colour themes that exist in styles.css (".ad-creative--sunset" etc.). */
  public static final List<String> THEMES =
      List.of("sunset", "forest", "night", "clay", "ocean", "berry");

  /** Makes the list unmodifiable. */
  public AdCreative {
    sections = List.copyOf(sections);
  }

  /** True if this ad is targeted at the section. */
  public boolean targets(Section section) {
    return sections.contains(section);
  }
}
