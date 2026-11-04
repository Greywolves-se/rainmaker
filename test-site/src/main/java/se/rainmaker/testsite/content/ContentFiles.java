package se.rainmaker.testsite.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads content files, with error messages that name the file. */
final class ContentFiles {

  private ContentFiles() {}

  /** The file's text. */
  static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException exception) {
      throw new ContentValidationException(
          file.getFileName().toString(), "cannot read the file", exception);
    }
  }

  /** The text parsed as JSON; {@code source} names the file in the error message. */
  static JsonNode parse(JsonMapper jsonMapper, String text, String source) {
    try {
      return jsonMapper.readTree(text);
    } catch (JacksonException exception) {
      throw new ContentValidationException(
          source, "invalid JSON (" + exception.getOriginalMessage() + ")", exception);
    }
  }
}
