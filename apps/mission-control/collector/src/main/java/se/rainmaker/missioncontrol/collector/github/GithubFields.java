package se.rainmaker.missioncontrol.collector.github;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/** Small helpers shared by the GitHub sources. */
final class GithubFields {

  private GithubFields() {}

  /** {@code gh-<repository name>-<number>}, e.g. {@code gh-rainmaker-12}. */
  static String itemId(String repository, int number) {
    String name = repository.substring(repository.indexOf('/') + 1).toLowerCase(Locale.ROOT);
    return "gh-" + name + "-" + number;
  }

  /** The label names of an issue or pull request; none when the field is missing. */
  static List<String> labels(JsonNode item) {
    JsonNode labels = item.get("labels");
    if (labels == null || labels.isNull()) {
      return List.of();
    }
    return labels.valueStream().map(label -> label.get("name").asString()).toList();
  }

  /** A GitHub timestamp field, or null when it is missing or null. */
  static Instant instant(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : Instant.parse(value.asString());
  }
}
