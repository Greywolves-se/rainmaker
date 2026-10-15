package se.rainmaker.missioncontrol.collector.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Contract test: the Java records read and write exactly the shape of the JSON Schema. */
class SnapshotJsonTest {

  private static final JsonMapper JSON = SnapshotJson.mapper();

  @Test
  void readsTheSampleAndWritesItBackUnchanged() {
    Snapshot snapshot = JSON.readValue(ContractFiles.sampleJson(), Snapshot.class);

    JsonNode written = JSON.valueToTree(snapshot);

    assertThat(written).isEqualTo(ContractFiles.sampleTree());
  }

  @Test
  void writtenSnapshotMatchesSchema() {
    Instant now = Instant.parse("2026-11-02T06:00:00Z");
    Snapshot snapshot =
        new Snapshot(
            Snapshot.SCHEMA_VERSION,
            now,
            new Project(
                "Example project",
                List.of(new Project.Milestone("goal-1", "Goal 1", LocalDate.of(2026, 12, 18)))),
            List.of(new Source("github", "GitHub", Source.Status.OK, now, null)),
            null,
            List.of(
                new WorkItem(
                    "1",
                    "Collector skeleton",
                    WorkItem.Status.IN_PROGRESS,
                    List.of(),
                    List.of(),
                    true,
                    now,
                    null,
                    "github",
                    "https://github.com/example-org/example-repo/issues/1")),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    assertThat(ContractFiles.schema().validate(JSON.valueToTree(snapshot))).isEmpty();
  }

  @Test
  void failsOnFieldTheRecordsDoNotKnow() {
    ObjectNode sample = ContractFiles.sampleTree();
    ((ObjectNode) sample.get("risks").get(0)).put("probability", "high");

    assertThatThrownBy(() -> JSON.treeToValue(sample, Snapshot.class))
        .isInstanceOf(UnrecognizedPropertyException.class)
        .hasMessageContaining("probability");
  }
}
