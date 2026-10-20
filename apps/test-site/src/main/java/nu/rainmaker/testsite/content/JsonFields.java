package nu.rainmaker.testsite.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Small helpers for reading required fields from parsed JSON, with error messages that name the
 * file and the field. Used by the content parsers.
 */
final class JsonFields {

  private JsonFields() {}

  /** Returns the node if it is a JSON object, otherwise throws. */
  static JsonNode requireObject(JsonNode node, String source, String path) {
    if (node == null || !node.isObject()) {
      throw new ContentValidationException(source, '"' + path + "\" must be an object");
    }
    return node;
  }

  /** Returns a non-blank text field, otherwise throws. */
  static String requireText(JsonNode parent, String field, String source, String path) {
    JsonNode value = parent.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new ContentValidationException(source, '"' + path + "\" must be a non-empty string");
    }
    return value.asText();
  }

  /** Returns a non-empty array field, otherwise throws. */
  static JsonNode requireNonEmptyArray(JsonNode parent, String field, String source) {
    JsonNode value = parent.get(field);
    if (value == null || !value.isArray() || value.isEmpty()) {
      throw new ContentValidationException(source, '"' + field + "\" must be a non-empty array");
    }
    return value;
  }

  /** Returns a list of non-blank strings, otherwise throws. */
  static List<String> requireTextList(JsonNode parent, String field, String source, String path) {
    JsonNode array = parent.get(field);
    if (array == null || !array.isArray() || array.isEmpty()) {
      throw new ContentValidationException(
          source, '"' + path + "\" must be a non-empty array of strings");
    }
    List<String> texts = new ArrayList<>();
    for (JsonNode item : array) {
      if (!item.isTextual() || item.asText().isBlank()) {
        throw new ContentValidationException(
            source, '"' + path + "\" must contain only non-empty strings");
      }
      texts.add(item.asText());
    }
    return texts;
  }

  /** True when the field is missing or JSON null. */
  static boolean isAbsent(JsonNode parent, String field) {
    JsonNode value = parent.get(field);
    return value == null || value.isNull();
  }
}
