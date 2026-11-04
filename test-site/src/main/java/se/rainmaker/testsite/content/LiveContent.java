package se.rainmaker.testsite.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * The articles and the front page, kept up to date with content/ while the site runs (#159).
 *
 * <p>Every request asks for {@link #current()}, which reads {@code articles/*.json} and {@code
 * front-page.json} again and parses them only if their text changed. So publishing or editing an
 * article means saving a file: no restart. A change that fails the same checks as at start-up is
 * refused: it is logged once, with the file and the field, and the site keeps serving the last good
 * version. A broken file at start-up still stops the site.
 */
public final class LiveContent {

  private static final Logger LOG = LoggerFactory.getLogger(LiveContent.class);
  private static final String FRONT_PAGE = "front-page.json";

  private final Path contentDirectory;
  private final JsonMapper jsonMapper;

  private Map<String, String> currentFiles;
  private SiteContent current;
  private String lastRefusal;

  /**
   * Loads the content.
   *
   * @throws ContentValidationException if a file is missing or broken (fail fast at start-up)
   */
  public LiveContent(Path contentDirectory, JsonMapper jsonMapper) {
    this.contentDirectory = contentDirectory;
    this.jsonMapper = jsonMapper;
    this.currentFiles = readFiles();
    this.current = parse(currentFiles);
    LOG.info(
        "Loaded {} articles from {}",
        current.articles().all().size(),
        contentDirectory.toAbsolutePath());
  }

  /** The content as it is now, or the last good version if the files changed into broken ones. */
  public synchronized SiteContent current() {
    try {
      Map<String, String> files = readFiles();
      if (!files.equals(currentFiles)) {
        current = parse(files);
        currentFiles = files;
        LOG.info("Reloaded content: {} articles", current.articles().all().size());
      }
      lastRefusal = null;
    } catch (ContentValidationException exception) {
      // Refused on purpose: the site keeps the last good version. Logged once per problem.
      if (!exception.getMessage().equals(lastRefusal)) {
        lastRefusal = exception.getMessage();
        LOG.error("Content change refused, still serving the last good version: {}", lastRefusal);
      }
    }
    return current;
  }

  /** The text of every article file, by file name in name order, and of front-page.json last. */
  private Map<String, String> readFiles() {
    Path directory = contentDirectory.resolve("articles");
    List<Path> articleFiles;
    try (Stream<Path> listing = Files.list(directory)) {
      articleFiles = listing.filter(path -> path.toString().endsWith(".json")).sorted().toList();
    } catch (IOException exception) {
      throw new ContentValidationException(
          directory.toString(), "cannot list the article folder", exception);
    }
    Map<String, String> files = new LinkedHashMap<>();
    for (Path file : articleFiles) {
      files.put(file.getFileName().toString(), ContentFiles.read(file));
    }
    files.put(FRONT_PAGE, ContentFiles.read(contentDirectory.resolve(FRONT_PAGE)));
    return files;
  }

  private SiteContent parse(Map<String, String> files) {
    List<Article> articles =
        files.entrySet().stream()
            .filter(file -> !file.getKey().equals(FRONT_PAGE))
            .map(
                file ->
                    ArticleParser.parse(
                        ContentFiles.parse(jsonMapper, file.getValue(), file.getKey()),
                        file.getKey()))
            .toList();
    ArticleRepository repository = new ArticleRepository(articles);
    FrontPage frontPage =
        FrontPage.parse(
            ContentFiles.parse(jsonMapper, files.get(FRONT_PAGE), FRONT_PAGE), repository);
    return new SiteContent(repository, frontPage);
  }
}
