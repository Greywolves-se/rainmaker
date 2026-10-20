package nu.rainmaker.testsite.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import nu.rainmaker.testsite.config.SiteProperties;
import nu.rainmaker.testsite.content.AdCreative;
import nu.rainmaker.testsite.content.AdInventory;
import nu.rainmaker.testsite.feed.FeedWriter;
import nu.rainmaker.testsite.feed.TagContainer;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-readable addresses.
 *
 * <ul>
 *   <li>{@code /rss.xml}, {@code /sitemap.xml}, {@code /robots.txt} - for Content Ingestion (S5)
 *   <li>{@code /eval/labels.csv} - expected zones, for the AI evaluation (S6)
 *   <li>{@code /ads/creatives.json} - the fake ad server, used by static/js/ads.js
 *   <li>{@code /tag-manager/container.json} - used by static/js/tag-manager.js
 *   <li>{@code /health} - "ok", for uptime checks
 * </ul>
 */
@RestController
public class FeedController {

  private static final MediaType RSS = utf8("application", "rss+xml");
  private static final MediaType XML = utf8("application", "xml");
  private static final MediaType CSV = utf8("text", "csv");
  private static final MediaType TEXT = utf8("text", "plain");

  private final FeedWriter feedWriter;
  private final AdInventory adInventory;
  private final TagContainer tagContainer;

  /** Creates the controller. */
  public FeedController(
      FeedWriter feedWriter, AdInventory adInventory, SiteProperties siteProperties) {
    this.feedWriter = feedWriter;
    this.adInventory = adInventory;
    this.tagContainer = TagContainer.forSite(siteProperties);
  }

  /** RSS 2.0 feed. */
  @GetMapping("/rss.xml")
  public ResponseEntity<String> rss(Locale locale) {
    return ResponseEntity.ok().contentType(RSS).body(feedWriter.rss(locale));
  }

  /** Google News-style sitemap. */
  @GetMapping("/sitemap.xml")
  public ResponseEntity<String> sitemap(Locale locale) {
    return ResponseEntity.ok().contentType(XML).body(feedWriter.newsSitemap(locale));
  }

  /** Crawler rules. */
  @GetMapping("/robots.txt")
  public ResponseEntity<String> robots() {
    return ResponseEntity.ok().contentType(TEXT).body(feedWriter.robotsTxt());
  }

  /** Expected zone per article. */
  @GetMapping("/eval/labels.csv")
  public ResponseEntity<String> evaluationLabels() {
    return ResponseEntity.ok().contentType(CSV).body(feedWriter.evaluationLabels());
  }

  /** All ad creatives. */
  @GetMapping("/ads/creatives.json")
  public ResponseEntity<Map<String, List<AdCreative>>> adCreatives() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .body(Map.of("creatives", adInventory.creatives()));
  }

  /** The tag manager's container. */
  @GetMapping("/tag-manager/container.json")
  public ResponseEntity<TagContainer> tagContainer() {
    return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(tagContainer);
  }

  /** Health check. */
  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok().contentType(TEXT).cacheControl(CacheControl.noStore()).body("ok");
  }

  private static MediaType utf8(String type, String subtype) {
    return new MediaType(type, subtype, StandardCharsets.UTF_8);
  }
}
