package se.rainmaker.missioncontrol.collector.github;

import java.time.Instant;
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

  /** A GitHub timestamp field, or null when it is missing or null. */
  static Instant instant(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : Instant.parse(value.asString());
  }
}
