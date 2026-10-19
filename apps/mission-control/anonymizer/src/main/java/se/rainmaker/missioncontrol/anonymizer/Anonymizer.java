package se.rainmaker.missioncontrol.anonymizer;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides people's names before text is sent to an external AI model, and puts them back in the
 * answer (#115, ADR 0002; reused by the Ask Navigator backend, ADR 0003).
 *
 * <p>Every known name or login becomes a neutral label, {@code Person A}, {@code Person B} …:
 *
 * <ul>
 *   <li>Labels follow the names in alphabetical order, so the same names always get the same
 *       labels, whatever order they come in.
 *   <li>Names match as whole words and without case ({@code KIM EK} is {@code Kim Ek}), also
 *       outside English letters. A longer name wins over a shorter one inside it.
 *   <li>The text is replaced in one pass, so a label is never replaced again.
 *   <li>{@link #restore} puts the names back in the spelling they were given. A label the model
 *       invents, which was never handed out, is left as it is.
 * </ul>
 *
 * <p><b>Known limit:</b> only the given names are hidden. A name that appears only in free text,
 * and is not in the list, is sent as written. Two names of one person (a full name and a login) get
 * two labels. The caller builds the list from every person field it has.
 *
 * <p>Immutable and safe to share between threads. No dependencies besides the JDK.
 */
public final class Anonymizer {

  private static final String LABEL_PREFIX = "Person ";

  /** Not a letter, digit or underscore around a match, so only whole words are replaced. */
  private static final String WORD_START = "(?<![\\p{L}\\p{N}_])";

  private static final String WORD_END = "(?![\\p{L}\\p{N}_])";

  private final Map<String, String> labelByName;
  private final Map<String, String> nameByLabel;
  private final Pattern namePattern;
  private final Pattern labelPattern;

  private Anonymizer(Map<String, String> labelByName, Map<String, String> nameByLabel) {
    this.labelByName = labelByName;
    this.nameByLabel = nameByLabel;
    this.namePattern = alternatives(labelByName.keySet().stream().toList(), true);
    this.labelPattern = alternatives(nameByLabel.keySet().stream().toList(), false);
  }

  /**
   * An anonymizer for these names and logins. Blank and {@code null} entries are ignored, and names
   * that differ only in case or surrounding spaces count as one (the first spelling wins).
   */
  public static Anonymizer forNames(Collection<String> names) {
    // Sorted without case, the same character-by-character rule the pattern matches with, so a
    // matched name is always found again (lower-casing a whole string can change its length).
    Map<String, String> spellingByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (String name : names) {
      if (name != null && !name.isBlank()) {
        spellingByName.putIfAbsent(name.strip(), name.strip());
      }
    }

    Map<String, String> labelByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<String, String> nameByLabel = new LinkedHashMap<>();
    int number = 0;
    for (String spelling : spellingByName.values()) {
      String label = LABEL_PREFIX + letters(++number);
      labelByName.put(spelling, label);
      nameByLabel.put(label, spelling);
    }
    return new Anonymizer(labelByName, nameByLabel);
  }

  /** {@code text} with every known name replaced by its label; {@code null} stays {@code null}. */
  public String anonymize(String text) {
    return replace(text, namePattern, labelByName::get);
  }

  /** {@code text} with every label handed out by this anonymizer replaced by its name. */
  public String restore(String text) {
    return replace(text, labelPattern, nameByLabel::get);
  }

  /** The labels in use, in order ({@code Person A}, {@code Person B} and so on). */
  public List<String> labels() {
    return List.copyOf(nameByLabel.keySet());
  }

  private static String replace(
      String text, Pattern pattern, Function<String, String> replacement) {
    if (text == null || pattern == null) {
      return text;
    }
    Matcher matcher = pattern.matcher(text);
    return matcher.replaceAll(
        match ->
            Matcher.quoteReplacement(Objects.requireNonNull(replacement.apply(match.group()))));
  }

  /**
   * One pattern that matches any of {@code words} as a whole word, longest first, so "Kim Ek" wins
   * over "Kim". {@code null} when there are no words.
   */
  private static Pattern alternatives(List<String> words, boolean ignoreCase) {
    if (words.isEmpty()) {
      return null;
    }
    String alternatives =
        words.stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote)
            .reduce((left, right) -> left + "|" + right)
            .orElseThrow();
    int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
    return Pattern.compile(WORD_START + "(?:" + alternatives + ")" + WORD_END, flags);
  }

  /** 1 → A, 26 → Z, 27 → AA, like spreadsheet columns. */
  static String letters(int number) {
    StringBuilder letters = new StringBuilder();
    for (int rest = number; rest > 0; rest = (rest - 1) / 26) {
      letters.insert(0, (char) ('A' + (rest - 1) % 26));
    }
    return letters.toString();
  }
}
