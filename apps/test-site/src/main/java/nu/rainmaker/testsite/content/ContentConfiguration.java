package nu.rainmaker.testsite.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import nu.rainmaker.testsite.config.SiteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads all content files once, when the application starts, and makes them available to the rest
 * of the site. The files are:
 *
 * <ul>
 *   <li>{@code content/articles/*.json} - one file per article
 *   <li>{@code content/front-page.json} - editorial front page choices
 *   <li>{@code content/ads.json} - fake ads
 * </ul>
 *
 * <p>A broken file stops the start-up with a message naming the file and field ("fail fast").
 */
@Configuration
public class ContentConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ContentConfiguration.class);

  private final Path contentDirectory;
  private final ObjectMapper objectMapper;

  /** Creates the configuration (constructor injection, as the project rules require). */
  public ContentConfiguration(SiteProperties siteProperties, ObjectMapper objectMapper) {
    this.contentDirectory = siteProperties.contentDirectory();
    this.objectMapper = objectMapper;
  }

  /** All articles from content/articles/. */
  @Bean
  public ArticleRepository articleRepository() {
    Path directory = contentDirectory.resolve("articles");
    List<Path> files;
    try (Stream<Path> listing = Files.list(directory)) {
      files = listing.filter(path -> path.toString().endsWith(".json")).sorted().toList();
    } catch (IOException exception) {
      throw new ContentValidationException(
          directory.toString(), "cannot list the article folder", exception);
    }
    List<Article> articles =
        files.stream()
            .map(file -> ArticleParser.parse(readJson(file), file.getFileName().toString()))
            .toList();
    LOG.info("Loaded {} articles from {}", articles.size(), directory.toAbsolutePath());
    return new ArticleRepository(articles);
  }

  /** The editorial front page from content/front-page.json. */
  @Bean
  public FrontPage frontPage(ArticleRepository articleRepository) {
    return FrontPage.parse(
        readJson(contentDirectory.resolve("front-page.json")), articleRepository);
  }

  /** The fake ads from content/ads.json. */
  @Bean
  public AdInventory adInventory() {
    return AdInventory.parse(readJson(contentDirectory.resolve("ads.json")));
  }

  private JsonNode readJson(Path file) {
    String source = file.getFileName().toString();
    try {
      return objectMapper.readTree(Files.readString(file));
    } catch (JsonProcessingException exception) {
      throw new ContentValidationException(
          source, "invalid JSON (" + exception.getOriginalMessage() + ")", exception);
    } catch (IOException exception) {
      throw new ContentValidationException(source, "cannot read the file", exception);
    }
  }
}
