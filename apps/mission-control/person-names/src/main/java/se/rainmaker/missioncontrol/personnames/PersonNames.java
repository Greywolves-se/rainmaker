package se.rainmaker.missioncontrol.personnames;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * The names to hide before text goes to an AI model, read from a snapshot's JSON (#136, ADR 0002,
 * ADR 0003). The collector (#115) and the Ask Navigator backend (#117) both use this, so they can
 * never hide different names.
 *
 * <ul>
 *   <li>Person fields: work item assignees, pull request authors, the owners of decisions, risks,
 *       questions and changes, and document last editors.
 *   <li>Owner fields that list several people are split, so each name inside is hidden too.
 *   <li>The first name of a multi-word name is added too (team decision on #136): only when it has
 *       at least {@value #MIN_FIRST_NAME} letters, starts with a capital and is not a common word,
 *       so "Kim Ek" also hides "Kim", but "Al Berg" does not hide "Al".
 * </ul>
 *
 * <p>Known limit: a name that is in no person field is not found.
 */
public final class PersonNames {

  static final int MIN_FIRST_NAME = 3;

  /**
   * First words that are not names, so a group such as "Team Rainmaker" never hides "Team", and a
   * role owner such as "Senior Dev" or "Customer (Staffan)" never hides "Senior" or "Customer".
   * Extend it when a role word in the data files slips through.
   */
  static final Set<String> COMMON_WORDS =
      Set.of(
          "senior",
          "customer",
          "kund",
          "team",
          "teamet",
          "the",
          "mission",
          "project",
          "projekt",
          "product",
          "produkt",
          "developers",
          "utvecklare",
          "owners",
          "ägare",
          "admin",
          "all",
          "alla");

  /** A snapshot list and the person field on each of its items. */
  private record PersonField(String list, String field) {}

  private static final PersonField ASSIGNEES = new PersonField("workItems", "assignees");
  private static final List<PersonField> SINGLE_PERSON_FIELDS =
      List.of(
          new PersonField("pullRequests", "author"), new PersonField("documents", "lastEditor"));
  private static final List<String> OWNER_LISTS =
      List.of("decisions", "risks", "questions", "changes");

  /** Splits owner fields such as "Mission Control developers (Emil, Gabriel, Mai)". */
  private static final Pattern NAME_LIST =
      Pattern.compile("\\s*(?:[,/&()]|\\band\\b|\\boch\\b)\\s*");

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private PersonNames() {}

  /** Every name to hide in {@code snapshot}; empty when it is {@code null} or has no people. */
  public static Set<String> fromSnapshot(JsonNode snapshot) {
    if (snapshot == null) {
      return Collections.emptySet();
    }
    Set<String> names = new TreeSet<>();
    for (JsonNode item : snapshot.path(ASSIGNEES.list())) {
      for (JsonNode person : item.path(ASSIGNEES.field())) {
        addPerson(names, person);
      }
    }
    for (PersonField person : SINGLE_PERSON_FIELDS) {
      for (JsonNode item : snapshot.path(person.list())) {
        addPerson(names, item.path(person.field()));
      }
    }
    for (String list : OWNER_LISTS) {
      for (JsonNode item : snapshot.path(list)) {
        String owner = text(item.path("owner"));
        if (owner != null) {
          ownerNames(owner).forEach(name -> addWithFirstName(names, name));
        }
      }
    }
    return Collections.unmodifiableSet(names);
  }

  /**
   * The names in an owner field: the whole field, plus each name in a list. Only parts that start
   * with a capital letter count, and the group label before a parenthesis does not, so "Mission
   * Control developers (Emil, Gabriel, Mai)" gives Emil, Gabriel and Mai, and "Kalle och teamet"
   * gives Kalle, never "teamet" or "Mission Control developers".
   */
  static Set<String> ownerNames(String owner) {
    Set<String> names = new LinkedHashSet<>();
    names.add(owner);
    int bracket = owner.indexOf('(');
    String list = bracket < 0 ? owner : owner.substring(bracket + 1);
    Arrays.stream(NAME_LIST.split(list))
        .map(String::strip)
        .filter(part -> part.length() > 1 && Character.isUpperCase(part.charAt(0)))
        .forEach(names::add);
    return names;
  }

  /** The first word of a multi-word name, when it may be a first name on its own. */
  static Optional<String> firstName(String name) {
    String[] words = WHITESPACE.split(name.strip());
    if (words.length < 2) {
      return Optional.empty();
    }
    String first = words[0];
    boolean looksLikeName =
        first.codePointCount(0, first.length()) >= MIN_FIRST_NAME
            && Character.isUpperCase(first.codePointAt(0))
            && first.codePoints().allMatch(Character::isLetter)
            && !COMMON_WORDS.contains(first.toLowerCase(Locale.ROOT));
    return looksLikeName ? Optional.of(first) : Optional.empty();
  }

  private static void addPerson(Set<String> names, JsonNode person) {
    String name = text(person);
    if (name != null) {
      addWithFirstName(names, name);
    }
  }

  private static void addWithFirstName(Set<String> names, String name) {
    names.add(name);
    firstName(name).ifPresent(names::add);
  }

  private static String text(JsonNode node) {
    if (node == null || !node.isString()) {
      return null;
    }
    String text = node.asString().strip();
    return text.isEmpty() ? null : text;
  }
}
