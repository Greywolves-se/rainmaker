package se.rainmaker.infra;

import java.util.List;
import java.util.Map;
import software.constructs.Construct;

/**
 * Reads settings from the CDK context: {@code cdk.json} or {@code -c key=value} on the command
 * line. A missing or wrong value stops the synth with a message naming the key, instead of
 * producing a template with a wrong account or ARN.
 */
final class ContextValues {

  private ContextValues() {}

  /** The JSON object stored under {@code key}. */
  static Map<?, ?> requireObject(Construct scope, String key) {
    Object value = scope.getNode().tryGetContext(key);
    if (!(value instanceof Map<?, ?> object)) {
      throw new IllegalArgumentException(
          "CDK context \"" + key + "\" must be an object (see infra/cdk.json)");
    }
    return object;
  }

  /** The non-blank string {@code field} of the object stored under {@code key}. */
  static String requireText(Map<?, ?> object, String key, String field) {
    if (!(object.get(field) instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException(
          "CDK context \"" + key + "\": \"" + field + "\" must be a non-empty string");
    }
    return text;
  }

  /** The non-empty list of non-blank strings {@code field} of the object under {@code key}. */
  static List<String> requireTextList(Map<?, ?> object, String key, String field) {
    if (!(object.get(field) instanceof List<?> items)
        || items.isEmpty()
        || !items.stream().allMatch(item -> item instanceof String text && !text.isBlank())) {
      throw new IllegalArgumentException(
          "CDK context \"" + key + "\": \"" + field + "\" must be a non-empty list of strings");
    }
    return items.stream().map(String.class::cast).toList();
  }
}
