package se.rainmaker.missioncontrol.anonymizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnonymizerTest {

  @Test
  void replacesEveryNameWithLabelAndRestoresIt() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Kim Ek", "gabbson42"));

    String hidden = anonymizer.anonymize("Kim Ek reviews the PR by gabbson42.");

    // Labels follow the names in alphabetical order, so the same names always get the same labels.
    assertThat(hidden).isEqualTo("Person B reviews the PR by Person A.");
    assertThat(anonymizer.restore(hidden)).isEqualTo("Kim Ek reviews the PR by gabbson42.");
  }

  @Test
  void sameNamesInAnyOrderGetSameLabels() {
    Anonymizer first = Anonymizer.forNames(List.of("Simon", "Peter", "Kim Ek"));
    Anonymizer second = Anonymizer.forNames(List.of("Kim Ek", "Simon", "Peter", "peter"));

    String text = "Peter asks Simon and Kim Ek.";

    assertThat(first.anonymize(text))
        .isEqualTo(second.anonymize(text))
        .isEqualTo("Person B asks Person C and Person A.");
  }

  @Test
  void matchesWithoutCaseAndRestoresTheGivenSpelling() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Kim Ek"));

    assertThat(anonymizer.anonymize("what is KIM EK working on?"))
        .isEqualTo("what is Person A working on?");
    assertThat(anonymizer.restore("Person A works on #115.")).isEqualTo("Kim Ek works on #115.");
  }

  /** The match and the lookup must agree on case even where lower-casing changes the length. */
  @Test
  void dottedCapitalLetterIsHiddenWithoutCrashing() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Kim"));

    assertThat(anonymizer.anonymize("KİM and kim")).isEqualTo("Person A and Person A");
  }

  @Test
  void longerNameWinsOverShorterNameInside() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Kim", "Kim Ek"));

    assertThat(anonymizer.anonymize("Kim Ek and Kim")).isEqualTo("Person B and Person A");
  }

  @Test
  void onlyWholeWordsAreReplaced() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Ek", "Mai"));

    assertThat(anonymizer.anonymize("Ekonomi in Maine, but Ek and Mai."))
        .isEqualTo("Ekonomi in Maine, but Person A and Person B.");
  }

  @Test
  void handlesLettersOutsideEnglishAndMentions() {
    Anonymizer anonymizer =
        Anonymizer.forNames(List.of("Gabriel Landvik Åkerman", "limE-Kod", "Åsa"));

    assertThat(anonymizer.anonymize("@limE-Kod asked Gabriel Landvik Åkerman; Åsa agreed."))
        .isEqualTo("@Person B asked Person A; Person C agreed.");
    assertThat(anonymizer.anonymize("Påsar and Åsalind stay."))
        .isEqualTo("Påsar and Åsalind stay.");
  }

  @Test
  void namesWithRegexCharactersAreTakenLiterally() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("a.b", "c+d (team)"));

    assertThat(anonymizer.anonymize("a.b, axb, c+d (team)")).isEqualTo("Person A, axb, Person B");
  }

  @Test
  void textIsReplacedInOnePassSoLabelsAreNeverReplacedAgain() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Person", "Kim"));

    String hidden = anonymizer.anonymize("Kim and Person");

    assertThat(hidden).isEqualTo("Person A and Person B");
    assertThat(anonymizer.restore(hidden)).isEqualTo("Kim and Person");
  }

  @Test
  void blankNullAndDuplicateNamesAreIgnored() {
    List<String> names = new ArrayList<>(Arrays.asList("", "  ", null, "Kim", " Kim "));

    Anonymizer anonymizer = Anonymizer.forNames(names);

    assertThat(anonymizer.labels()).containsExactly("Person A");
    assertThat(anonymizer.anonymize("Kim")).isEqualTo("Person A");
  }

  @Test
  void withoutNamesTextIsUnchanged() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of());

    assertThat(anonymizer.anonymize("Kim Ek")).isEqualTo("Kim Ek");
    assertThat(anonymizer.restore("Person A")).isEqualTo("Person A");
    assertThat(anonymizer.anonymize(null)).isNull();
    assertThat(anonymizer.restore(null)).isNull();
  }

  @Test
  void labelsGoOnPastTheAlphabetAndRestoreLongestFirst() {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < 28; i++) {
      names.add("name" + (char) ('a' + i / 26) + (char) ('a' + i % 26));
    }
    Anonymizer anonymizer = Anonymizer.forNames(names);

    assertThat(anonymizer.labels()).contains("Person Z", "Person AA", "Person AB");
    assertThat(anonymizer.anonymize("nameba")).isEqualTo("Person AA");
    assertThat(anonymizer.restore("Person AA and Person A")).isEqualTo("nameba and nameaa");
  }

  /** Swedish genitive: "Kim Eks ansvar" must be hidden, and "Person As" must come back. */
  @Test
  void swedishGenitiveIsHiddenAndRestored() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Kim Ek"));

    String hidden = anonymizer.anonymize("Kim Eks ansvar och Kim Ek själv");

    assertThat(hidden).isEqualTo("Person As ansvar och Person A själv");
    assertThat(anonymizer.restore("Person As ansvar")).isEqualTo("Kim Eks ansvar");
  }

  /** A name ending in s, x or z takes no extra s in the Swedish genitive. */
  @Test
  void genitiveOfNameEndingInSibilantAddsNothing() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Lars", "Max"));

    assertThat(anonymizer.restore("Person As och Person Bs plan")).isEqualTo("Lars och Max plan");
  }

  @Test
  void englishPossessiveStillWorks() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Kim Ek"));

    assertThat(anonymizer.anonymize("Kim Ek's task")).isEqualTo("Person A's task");
    assertThat(anonymizer.restore("Person A's task")).isEqualTo("Kim Ek's task");
  }

  /** "Person A" must never change part of "Person AB" (27 or more people). */
  @Test
  void shortLabelNeverChangesPartOfLongerLabel() {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < 28; i++) {
      names.add("name" + (char) ('a' + i / 26) + (char) ('a' + i % 26));
    }
    Anonymizer anonymizer = Anonymizer.forNames(names);

    assertThat(anonymizer.restore("Person AB, Person A and Person ABs plan"))
        .isEqualTo("namebb, nameaa and namebbs plan");
  }

  @Test
  void unknownLabelsFromTheModelAreLeftAsTheyAre() {
    Anonymizer anonymizer = Anonymizer.forNames(List.of("Kim"));

    assertThat(anonymizer.restore("Person A and Person Q")).isEqualTo("Kim and Person Q");
  }
}
