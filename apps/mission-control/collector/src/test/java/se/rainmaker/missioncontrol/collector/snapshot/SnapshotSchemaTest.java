package se.rainmaker.missioncontrol.collector.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Schema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checks the snapshot JSON Schema and the shared sample in {@code apps/mission-control/contract}.
 */
class SnapshotSchemaTest {

  private static final Schema SCHEMA = ContractFiles.schema();

  @Test
  void sampleMatchesSchema() {
    assertThat(SCHEMA.validate(ContractFiles.sampleTree())).isEmpty();
  }

  @Test
  void acceptsSourcesTheSchemaDoesNotKnowYet() {
    ObjectNode snapshot = ContractFiles.sampleTree();
    ObjectNode slack = ((ArrayNode) snapshot.get("sources")).addObject();
    slack.put("id", "slack").put("name", "Slack").put("status", "disabled");
    slack.putNull("fetchedAt");
    slack.put("message", "Not connected yet");
    ((ObjectNode) snapshot.get("events").get(0)).put("source", "slack");

    assertThat(SCHEMA.validate(snapshot)).isEmpty();
  }

  @Test
  void rejectsUnknownField() {
    ObjectNode snapshot = ContractFiles.sampleTree();
    snapshot.put("costSek", 1200);

    assertThat(SCHEMA.validate(snapshot)).isNotEmpty();
  }

  @Test
  void rejectsMissingFieldEvenWhenItWouldBeNull() {
    ObjectNode snapshot = ContractFiles.sampleTree();
    ((ObjectNode) snapshot.get("workItems").get(0)).remove("closedAt");

    assertThat(SCHEMA.validate(snapshot)).isNotEmpty();
  }

  @Test
  void rejectsUnknownStatus() {
    ObjectNode snapshot = ContractFiles.sampleTree();
    ((ObjectNode) snapshot.get("workItems").get(0)).put("status", "doing");

    assertThat(SCHEMA.validate(snapshot)).isNotEmpty();
  }

  @Test
  void rejectsTimestampThatIsNotIso8601() {
    ObjectNode snapshot = ContractFiles.sampleTree();
    snapshot.put("generatedAt", "yesterday");

    assertThat(SCHEMA.validate(snapshot)).isNotEmpty();
  }

  @Test
  void navigatorMayBeNullButNotMissing() {
    ObjectNode switchedOff = ContractFiles.sampleTree();
    switchedOff.putNull("navigator");
    ObjectNode missing = ContractFiles.sampleTree();
    missing.remove("navigator");

    assertThat(SCHEMA.validate(switchedOff)).isEmpty();
    assertThat(SCHEMA.validate(missing)).isNotEmpty();
  }

  @Test
  void navigatorNeedsBothLanguages() {
    ObjectNode snapshot = ContractFiles.sampleTree();
    ((ObjectNode) snapshot.at("/navigator/pages/overview")).remove("sv");

    assertThat(SCHEMA.validate(snapshot)).isNotEmpty();
  }

  @Test
  void navigatorTextHasLimits() {
    ObjectNode tooMany = ContractFiles.sampleTree();
    ArrayNode deviations = (ArrayNode) tooMany.at("/navigator/pages/overview/en/deviations");
    for (int i = 0; i < 5; i++) {
      deviations.add("Another deviation");
    }
    ObjectNode tooLong = ContractFiles.sampleTree();
    ((ObjectNode) tooLong.at("/navigator/pages/overview/en")).put("summary", "x".repeat(601));
    ObjectNode empty = ContractFiles.sampleTree();
    ((ObjectNode) empty.at("/navigator/pages/overview/en")).put("summary", "");

    assertThat(SCHEMA.validate(tooMany)).isNotEmpty();
    assertThat(SCHEMA.validate(tooLong)).isNotEmpty();
    assertThat(SCHEMA.validate(empty)).isNotEmpty();
  }

  /** The web app counts the same way (snapshot.contract.test.ts), so both accept this text. */
  @Test
  void navigatorTextLimitCountsAnEmojiAsOneCharacter() {
    ObjectNode snapshot = ContractFiles.sampleTree();
    ((ObjectNode) snapshot.at("/navigator/pages/overview/en"))
        .put("summary", "x".repeat(599) + "😀");

    assertThat(SCHEMA.validate(snapshot)).isEmpty();
  }
}
