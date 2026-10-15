package se.rainmaker.missioncontrol.collector.datafiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads the YAML data files and remembers the line each item starts on, so problems can point to
 * it. A missing file means no items; problems are added to the shared list.
 */
final class DataFileReader {

  private static final Logger log = LoggerFactory.getLogger(DataFileReader.class);
  private static final YAMLMapper YAML = YAMLMapper.builder().build();

  private final Path folder;
  private final String filesUrl;
  private final List<String> problems;

  DataFileReader(Path folder, String filesUrl, List<String> problems) {
    this.folder = folder;
    this.filesUrl = filesUrl;
    this.problems = problems;
  }

  /** A file with one set of fields (e.g. project.yaml). */
  Optional<DataItem> object(String file) {
    Path path = folder.resolve(file);
    if (!exists(file, path)) {
      return Optional.empty();
    }
    try (JsonParser parser = YAML.createParser(path)) {
      if (parser.nextToken() == null) {
        return Optional.empty();
      }
      JsonNode node = parser.readValueAsTree();
      checkNothingFollows(file, parser);
      if (!node.isObject()) {
        problems.add(file + ": must be a set of fields (name: value)");
        return Optional.empty();
      }
      return Optional.of(new DataItem(file, node, link(file, 1), problems));
    } catch (JacksonException e) {
      invalidYaml(file, e);
      return Optional.empty();
    }
  }

  /** A file with a list of items (e.g. risks.yaml); ids must be unique within it. */
  List<DataItem> list(String file) {
    Path path = folder.resolve(file);
    List<DataItem> items = new ArrayList<>();
    if (!exists(file, path)) {
      return items;
    }
    try (JsonParser parser = YAML.createParser(path)) {
      JsonToken first = parser.nextToken();
      if (first == null) {
        return items;
      }
      if (first != JsonToken.START_ARRAY) {
        problems.add(file + ": must be a list of items (each item starts with '- ')");
        return items;
      }
      Set<String> ids = new HashSet<>();
      JsonToken token;
      while ((token = parser.nextToken()) != JsonToken.END_ARRAY && token != null) {
        int line = parser.currentTokenLocation().getLineNr();
        JsonNode node = parser.readValueAsTree();
        if (!node.isObject()) {
          // One problem for the item, instead of "is required" for each of its fields.
          problems.add(file + ":" + line + ": must be a set of fields (name: value)");
          continue;
        }
        items.add(new DataItem(file + ":" + line, node, link(file, line), problems));
        if (node.hasNonNull("id") && !ids.add(node.get("id").asString())) {
          problems.add(file + ":" + line + " id: " + node.get("id").asString() + " is used twice");
        }
      }
      checkNothingFollows(file, parser);
      return items;
    } catch (JacksonException e) {
      invalidYaml(file, e);
      return List.of();
    }
  }

  /** A missing file means no items; it is logged, so a misspelled file name shows up. */
  private static boolean exists(String file, Path path) {
    if (Files.exists(path)) {
      return true;
    }
    log.atInfo().addKeyValue("file", file).log("data file not found; no items");
    return false;
  }

  /**
   * Content after the list would otherwise be ignored without a word, e.g. items added below the
   * empty list {@code []} the files start with.
   */
  private void checkNothingFollows(String file, JsonParser parser) {
    int line;
    try {
      if (parser.nextToken() == null) {
        return;
      }
      line = parser.currentTokenLocation().getLineNr();
    } catch (JacksonException e) {
      // The YAML parser rejects most trailing content itself; give the same helpful hint.
      line = e.getLocation() == null ? 0 : e.getLocation().getLineNr();
    }
    problems.add(
        file + ":" + line + ": unexpected content; if you added items, remove the '[]' line");
  }

  private String link(String file, int line) {
    return filesUrl + "/" + file + "#L" + line;
  }

  private void invalidYaml(String file, JacksonException e) {
    // The parser's message can quote any part of the file, including long free text, so only
    // the line is reported. Problems found by our own checks may name short values such as ids.
    String line = e.getLocation() == null ? "" : ":" + e.getLocation().getLineNr();
    problems.add(file + line + ": is not valid YAML");
  }
}
