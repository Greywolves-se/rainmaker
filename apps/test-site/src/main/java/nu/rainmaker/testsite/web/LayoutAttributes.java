package nu.rainmaker.testsite.web;

import java.util.List;
import java.util.Map;
import nu.rainmaker.testsite.config.SiteProperties;
import nu.rainmaker.testsite.content.Section;
import org.springframework.stereotype.Component;

/**
 * The values every page's layout (templates/layout.html) needs: the menu sections, the site
 * settings and how the Rainmaker widget is added.
 *
 * <p>Used both for normal pages ({@link LayoutModelAdvice}) and for error pages ({@link
 * ErrorPageAdvice}), because Spring does not add model attributes to error handlers by itself.
 */
@Component
public class LayoutAttributes {

  private final SiteProperties siteProperties;

  /** Creates the component. */
  public LayoutAttributes(SiteProperties siteProperties) {
    this.siteProperties = siteProperties;
  }

  /** The attributes, by name. */
  public Map<String, Object> asMap() {
    return Map.of(
        "sections", List.of(Section.values()),
        "site", siteProperties,
        "rainmaker", siteProperties.rainmaker());
  }
}
