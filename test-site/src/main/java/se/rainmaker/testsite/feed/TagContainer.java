package se.rainmaker.testsite.feed;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import se.rainmaker.testsite.config.SiteProperties;

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
   * @param attributes set on the script element, like a real tag manager's custom attributes; left
   *     out of the JSON when there are none
   */
  public record Tag(
      String name,
      String src,
      String trigger,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> attributes) {

    /** Makes the attributes unmodifiable. */
    public Tag {
      attributes = Map.copyOf(attributes);
    }
  }

  /**
   * Builds the container for the site. Rainmaker is only in it when the site is configured to
   * install the widget through the tag manager. The widget checks consent itself (via __tcfapi), so
   * it loads on every page view - like the template install.
   */
  public static TagContainer forSite(SiteProperties site) {
    List<Tag> tags = new ArrayList<>();
    tags.add(new Tag("vintra-stats", STATS_SOURCE, "consent", Map.of()));
    if (site.rainmaker().loadedByTagManager()) {
      tags.add(
          new Tag(
              "rainmaker",
              site.rainmaker().scriptUrl(),
              "pageview",
              site.rainmaker().scriptAttributes()));
    }
    return new TagContainer("VTB-TEST-1", tags);
  }
}
