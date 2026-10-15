package se.rainmaker.missioncontrol.collector.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checks the snapshot JSON Schema and the shared sample in {@code apps/mission-control/contract}.
 */
class SnapshotSchemaTest {

  static final Path CONTRACT = Path.of("..", "contract");

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static Schema schema;

  @BeforeAll
  static void loadSchema() throws IOException {
    SchemaRegistry registry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder ->
                builder.schemaRegistryConfig(
                    SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
    schema =
        registry.getSchema(
            Files.readString(CONTRACT.resolve("snapshot.schema.json")), InputFormat.JSON);
  }

  @Test
  void sampleMatchesSchema() throws IOException {
    assertThat(validate(sample())).isEmpty();
  }

  @Test
  void acceptsSourcesTheSchemaDoesNotKnowYet() throws IOException {
    ObjectNode snapshot = sample();
    ObjectNode slack = ((ArrayNode) snapshot.get("sources")).addObject();
    slack.put("id", "slack").put("name", "Slack").put("status", "disabled");
    slack.putNull("fetchedAt");
    slack.put("message", "Not connected yet");
    ((ObjectNode) snapshot.get("events").get(0)).put("source", "slack");

    assertThat(validate(snapshot)).isEmpty();
  }

  @Test
  void rejectsUnknownField() throws IOException {
    ObjectNode snapshot = sample();
    snapshot.put("costSek", 1200);

    assertThat(validate(snapshot)).isNotEmpty();
  }

  @Test
  void rejectsMissingFieldEvenWhenItWouldBeNull() throws IOException {
    ObjectNode snapshot = sample();
    ((ObjectNode) snapshot.get("workItems").get(0)).remove("closedAt");

    assertThat(validate(snapshot)).isNotEmpty();
  }

  @Test
  void rejectsUnknownStatus() throws IOException {
    ObjectNode snapshot = sample();
    ((ObjectNode) snapshot.get("workItems").get(0)).put("status", "doing");

    assertThat(validate(snapshot)).isNotEmpty();
  }

  @Test
  void rejectsTimestampThatIsNotIso8601() throws IOException {
    ObjectNode snapshot = sample();
    snapshot.put("generatedAt", "yesterday");

    assertThat(validate(snapshot)).isNotEmpty();
  }

  private static ObjectNode sample() throws IOException {
    return (ObjectNode)
        JSON.readTree(Files.readString(CONTRACT.resolve("fixtures/snapshot.sample.json")));
  }

  private static List<Error> validate(ObjectNode snapshot) {
    return schema.validate(snapshot);
  }
}
