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
}
