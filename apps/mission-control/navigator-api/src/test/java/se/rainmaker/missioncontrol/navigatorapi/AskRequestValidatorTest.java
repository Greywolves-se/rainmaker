package se.rainmaker.missioncontrol.navigatorapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AskRequestValidatorTest {

  private final AskRequestValidator validator =
      new AskRequestValidator(Set.of("rainmaker", "mission-control"));

  private static AskRequest request(String project, String page, String question, String lang) {
    return new AskRequest(project, page, question, lang);
  }

  @Test
  void acceptsKnownProjectPageAndLanguageWithQuestion() {
    assertThat(validator.problems(request("rainmaker", "overview", "What is blocked?", "sv")))
        .isEmpty();
  }

  @Test
  void rejectsProjectThatIsNotInProjectsJson() {
    assertThat(validator.problems(request("../secrets", "overview", "Hi?", "en")))
        .containsExactly(AskRequestValidator.Problem.UNKNOWN_PROJECT);
  }

  @Test
  void acceptsOnlyTheThreePages() {
    assertThat(validator.problems(request("rainmaker", "development", "Hi?", "en"))).isEmpty();
    assertThat(validator.problems(request("rainmaker", "sources", "Hi?", "en"))).isEmpty();
    assertThat(validator.problems(request("rainmaker", "start-here", "Hi?", "en")))
        .containsExactly(AskRequestValidator.Problem.UNKNOWN_PAGE);
  }

  @Test
  void acceptsOnlySwedishAndEnglish() {
    assertThat(validator.problems(request("rainmaker", "overview", "Hi?", "de")))
        .containsExactly(AskRequestValidator.Problem.UNKNOWN_LANGUAGE);
  }

  @Test
  void rejectsAnEmptyOrBlankQuestion() {
    assertThat(validator.problems(request("rainmaker", "overview", "   ", "en")))
        .containsExactly(AskRequestValidator.Problem.EMPTY_QUESTION);
  }

  @Test
  void acceptsFiveHundredCharactersButNotMore() {
    assertThat(validator.problems(request("rainmaker", "overview", "a".repeat(500), "en")))
        .isEmpty();
    assertThat(validator.problems(request("rainmaker", "overview", "a".repeat(501), "en")))
        .containsExactly(AskRequestValidator.Problem.QUESTION_TOO_LONG);
  }

  @Test
  void countsAnEmojiAsOneCharacter() {
    // "😀" is two Java chars but one character for the reader.
    assertThat(validator.problems(request("rainmaker", "overview", "😀".repeat(500), "en")))
        .isEmpty();
  }

  @Test
  void treatsMissingFieldsAsProblemsInsteadOfFailing() {
    assertThat(validator.problems(request(null, null, null, null)))
        .containsExactly(
            AskRequestValidator.Problem.UNKNOWN_PROJECT,
            AskRequestValidator.Problem.UNKNOWN_PAGE,
            AskRequestValidator.Problem.UNKNOWN_LANGUAGE,
            AskRequestValidator.Problem.EMPTY_QUESTION);
  }
}
