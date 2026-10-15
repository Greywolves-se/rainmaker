package se.rainmaker.missioncontrol.collector.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * Contract test: every Java enum has exactly the values the schema allows. The sample cannot cover
 * every value, so this compares the lists directly.
 */
class SnapshotEnumTest {

  private static final JsonNode DEFINITIONS = ContractFiles.schemaTree().get("$defs");

  static Stream<Arguments> enumsAndSchemaFields() {
    return Stream.of(
        Arguments.of(Source.Status.class, "source", "status"),
        Arguments.of(WorkItem.Status.class, "workItem", "status"),
        Arguments.of(PullRequest.State.class, "pullRequest", "state"),
        Arguments.of(PullRequest.Review.class, "pullRequest", "review"),
        Arguments.of(PullRequest.Checks.class, "pullRequest", "checks"),
        Arguments.of(Build.Status.class, "build", "status"),
        Arguments.of(Release.Environment.class, "release", "environment"),
        Arguments.of(Decision.Status.class, "decision", "status"),
        Arguments.of(Risk.Level.class, "risk", "level"),
        Arguments.of(Question.Status.class, "question", "status"),
        Arguments.of(Link.Category.class, "link", "category"));
  }

  @ParameterizedTest(name = "{1}.{2}")
  @MethodSource("enumsAndSchemaFields")
  void javaEnumHasExactlyTheSchemaValues(
      Class<? extends Enum<?>> javaEnum, String definition, String field) {
    JsonNode schemaValues = DEFINITIONS.get(definition).get("properties").get(field).get("enum");

    assertThat(Arrays.stream(javaEnum.getEnumConstants()).map(SnapshotEnumTest::jsonValue))
        .containsExactlyInAnyOrderElementsOf(
            schemaValues.valueStream().map(JsonNode::asString).toList());
  }

  private static String jsonValue(Enum<?> constant) {
    return constant.name().toLowerCase(Locale.ROOT);
  }
}
