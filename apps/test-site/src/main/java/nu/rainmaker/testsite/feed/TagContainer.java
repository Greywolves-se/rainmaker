package nu.rainmaker.testsite.feed;

import java.util.ArrayList;
import java.util.List;
import nu.rainmaker.testsite.config.SiteProperties;

/**
 * The fake tag manager's container: which scripts static/js/tag-manager.js loads, and when.
 *
 * @param containerId shown in the dataLayer, like a GTM container id
 * @param tags the scripts to load
 */
public record TagContainer(String containerId, List<Tag> tags) {

  /** Path of the fake statistics script, loaded only after consent. */
  static final String STATS_SOURCE = "/static/js/vintra-stats.js";

  /** Makes the list unmodifiable. */
  public TagContainer {
    tags = List.copyOf(tags);
  }

  /**
   * One script the tag manager loads.
   *
   * @param name shown in the page as data-tag-name
   * @param src address of the script
   * @param trigger "pageview" (load right away) or "consent" (only after "Godkänn alla")
   */
  public record Tag(String name, String src, String trigger) {}

  /**
   * Builds the container for the site. Rainmaker is only in it when the site is configured to
   * install the widget through the tag manager. The widget checks consent itself (via __tcfapi), so
   * it loads on every page view - like the template install.
   */
  public static TagContainer forSite(SiteProperties site) {
    List<Tag> tags = new ArrayList<>();
    tags.add(new Tag("vintra-stats", STATS_SOURCE, "consent"));
    if (site.rainmaker().loadedByTagManager()) {
      tags.add(new Tag("rainmaker", site.rainmaker().scriptUrl(), "pageview"));
    }
    return new TagContainer("VTB-TEST-1", tags);
  }
}
