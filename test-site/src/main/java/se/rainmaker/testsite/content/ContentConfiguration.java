package se.rainmaker.testsite.content;

import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.rainmaker.testsite.config.SiteProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes the content files available to the rest of the site. The files are:
 *
 * <ul>
 *   <li>{@code content/articles/*.json} - one file per article
 *   <li>{@code content/front-page.json} - editorial front page choices
 *   <li>{@code content/ads.json} - fake ads
 * </ul>
 *
 * <p>Articles and the front page follow the files while the site runs ({@link LiveContent}); the
 * ads are read once. A broken file stops the start-up with a message naming the file and field
 * ("fail fast").
 */
@Configuration
public class ContentConfiguration {

  private final Path contentDirectory;
  private final JsonMapper jsonMapper;

  /** Creates the configuration (constructor injection, as the project rules require). */
  public ContentConfiguration(SiteProperties siteProperties, JsonMapper jsonMapper) {
    this.contentDirectory = siteProperties.contentDirectory();
    this.jsonMapper = jsonMapper;
  }

  /** The articles and the front page, kept up to date with content/. */
  @Bean
  public LiveContent liveContent() {
    return new LiveContent(contentDirectory, jsonMapper);
  }

  /** The fake ads from content/ads.json. */
  @Bean
  public AdInventory adInventory() {
    Path file = contentDirectory.resolve("ads.json");
    return AdInventory.parse(ContentFiles.parse(jsonMapper, ContentFiles.read(file), "ads.json"));
  }
}
