package se.rainmaker.missioncontrol.collector.datafiles;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * One item in a data file. Each getter checks its field; a problem is recorded as {@code <where>
 * <field>: <what is wrong>} (e.g. {@code risks.yaml:14 owner: is required}) and the getter returns
 * null, so all problems in a file are reported at once.
 */
final class DataItem {

  private final String where;
  private final JsonNode node;
  private final String url;
  private final List<String> problems;
  private final Set<String> knownFields = new HashSet<>();

  DataItem(String where, JsonNode node, String url, List<String> problems) {
    this.where = where;
    this.node = node;
    this.url = url;
    this.problems = problems;
  }

  /** Link to the item's line on GitHub. */
  String url() {
    return url;
  }

  String text(String field) {
    String value = optionalText(field);
    if (value == null && isAbsent(field)) {
      problem(field, "is required");
    }
    return value;
  }

  String optionalText(String field) {
    JsonNode value = field(field);
    if (value == null) {
      return null;
    }
    if (!value.isValueNode() || value.asString().isBlank()) {
      problem(field, "must be a short text");
      return null;
    }
    return value.asString().strip();
  }

  LocalDate date(String field) {
    LocalDate value = optionalDate(field);
    if (value == null && isAbsent(field)) {
      problem(field, "is required");
    }
    return value;
  }

  LocalDate optionalDate(String field) {
    JsonNode value = field(field);
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(value.asString());
    } catch (DateTimeParseException e) {
      problem(field, "must be a date like 2026-12-18");
      return null;
    }
  }

  /** A true/false field; false when it is left out. */
  boolean flag(String field) {
    JsonNode value = field(field);
    if (value == null) {
      return false;
    }
    if (!value.isBoolean()) {
      problem(field, "must be true or false");
      return false;
    }
    return value.booleanValue();
  }

  /** One of the enum's values, written in lowercase (e.g. {@code in_progress}). */
  <E extends Enum<E>> E choice(String field, Class<E> type) {
    String value = text(field);
    if (value == null) {
      return null;
    }
    for (E constant : type.getEnumConstants()) {
      if (constant.name().toLowerCase(Locale.ROOT).equals(value)) {
        return constant;
      }
    }
    problem(
        field,
        "must be one of "
            + Arrays.stream(type.getEnumConstants())
                .map(constant -> constant.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", ")));
    return null;
  }

  String link(String field) {
    String value = text(field);
    if (value == null) {
      return null;
    }
    try {
      URI uri = URI.create(value);
      if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
          && uri.getHost() != null) {
        return value;
      }
    } catch (IllegalArgumentException e) {
      // Not a URI at all; reported below like any other bad link.
    }
    problem(field, "must be an http(s) link");
    return null;
  }

  /** The items in a list field (e.g. milestones); empty when it is left out. */
  List<DataItem> items(String field) {
    JsonNode value = field(field);
    List<DataItem> items = new ArrayList<>();
    if (value == null) {
      return items;
    }
    if (!value.isArray()) {
      problem(field, "must be a list of items");
      return items;
    }
    for (int i = 0; i < value.size(); i++) {
      items.add(new DataItem(where + " " + field + "[" + i + "]", value.get(i), url, problems));
    }
    return items;
  }

  /** Reports fields no getter asked for, e.g. a misspelled {@code ownr}. Call it last. */
  void checkNoOtherFields() {
    if (!node.isObject()) {
      problems.add(where + ": must be a set of fields (name: value)");
      return;
    }
    node.propertyNames().stream()
        .filter(name -> !knownFields.contains(name))
        .forEach(name -> problem(name, "unknown field"));
  }

  private JsonNode field(String field) {
    knownFields.add(field);
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value;
  }

  private boolean isAbsent(String field) {
    return field(field) == null;
  }

  private void problem(String field, String what) {
    problems.add(where + " " + field + ": " + what);
  }
}
