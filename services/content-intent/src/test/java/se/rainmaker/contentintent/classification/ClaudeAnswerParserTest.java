package se.rainmaker.contentintent.classification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;

/**
 * Claude's answer must match the contract's schema exactly (#145, section 8); anything else counts
 * as failed, so a broken or manipulated answer can never make an article green.
 */
class ClaudeAnswerParserTest {

  private static final String VALID =
      """
      {"zone": "green", "topics": ["home", "food"],
       "reason": {"sv": "Praktiska råd om badrum.", "en": "Practical bathroom advice."}}
      """;

  private final ClaudeAnswerParser parser = new ClaudeAnswerParser();

  @Test
  void readsValidAnswer() {
    Optional<ClaudeAnswer> answer = parser.parse(VALID);

    assertThat(answer).isPresent();
    assertThat(answer.get().zone()).isEqualTo(Zone.GREEN);
    assertThat(answer.get().topics()).containsExactly(Topic.HOME, Topic.FOOD);
    assertThat(answer.get().reason())
        .isEqualTo(new Reason("Praktiska råd om badrum.", "Practical bathroom advice."));
  }

  @Test
  void readsTheHyphenatedTopic() {
    String json =
        """
        {"zone": "yellow", "topics": ["personal-finance"], "reason": {"sv": "a", "en": "b"}}
        """;

    assertThat(parser.parse(json))
        .hasValueSatisfying(
            answer -> assertThat(answer.topics()).containsExactly(Topic.PERSONAL_FINANCE));
  }

  @Test
  void dropsRepeatedTopicsAndKeepsTheirOrder() {
    String json =
        """
        {"zone": "green", "topics": ["food", "home", "food"], "reason": {"sv": "a", "en": "b"}}
        """;

    assertThat(parser.parse(json))
        .hasValueSatisfying(
            answer -> assertThat(answer.topics()).containsExactly(Topic.FOOD, Topic.HOME));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "not json",
        "[]",
        "\"green\"",
        // Wrapped in a Markdown code fence instead of plain JSON.
        "```json\n{\"zone\": \"green\", \"topics\": [\"home\"],"
            + " \"reason\": {\"sv\": \"a\", \"en\": \"b\"}}\n```",
        // Missing or unknown values.
        "{\"topics\": [\"home\"], \"reason\": {\"sv\": \"a\", \"en\": \"b\"}}",
        "{\"zone\": \"orange\", \"topics\": [\"home\"],"
            + " \"reason\": {\"sv\": \"a\", \"en\": \"b\"}}",
        "{\"zone\": \"GREEN\", \"topics\": [\"home\"], \"reason\": {\"sv\": \"a\", \"en\": \"b\"}}",
        "{\"zone\": \"green\", \"topics\": [\"politics\"],"
            + " \"reason\": {\"sv\": \"a\", \"en\": \"b\"}}",
        // Too few or too many topics.
        "{\"zone\": \"green\", \"topics\": [], \"reason\": {\"sv\": \"a\", \"en\": \"b\"}}",
        "{\"zone\": \"green\", \"topics\": [\"home\", \"food\", \"cars\", \"travel\"],"
            + " \"reason\": {\"sv\": \"a\", \"en\": \"b\"}}",
        // D-9: both languages, not blank.
        "{\"zone\": \"green\", \"topics\": [\"home\"], \"reason\": {\"sv\": \"a\"}}",
        "{\"zone\": \"green\", \"topics\": [\"home\"], \"reason\": {\"sv\": \"a\", \"en\": \" \"}}",
        "{\"zone\": \"green\", \"topics\": [\"home\"], \"reason\": \"text\"}",
        // Extra fields are not allowed, at the top or inside the reason.
        "{\"zone\": \"green\", \"topics\": [\"home\"], \"reason\": {\"sv\": \"a\", \"en\": \"b\"},"
            + " \"offers\": []}",
        "{\"zone\": \"green\", \"topics\": [\"home\"], \"reason\": {\"sv\": \"a\", \"en\": \"b\","
            + " \"de\": \"c\"}}"
      })
  void refusesAnythingElse(String text) {
    assertThat(parser.parse(text)).isEmpty();
  }

  @Test
  void refusesReasonLongerThan300Characters() {
    String longText = "x".repeat(301);
    String json =
        "{\"zone\": \"green\", \"topics\": [\"home\"], \"reason\": {\"sv\": \""
            + longText
            + "\", \"en\": \"b\"}}";

    assertThat(parser.parse(json)).isEmpty();
  }

  @Test
  void acceptsReasonOfExactly300Characters() {
    String text = "x".repeat(300);
    String json =
        "{\"zone\": \"green\", \"topics\": [\"home\"], \"reason\": {\"sv\": \""
            + text
            + "\", \"en\": \"b\"}}";

    assertThat(parser.parse(json)).isPresent();
  }
}
