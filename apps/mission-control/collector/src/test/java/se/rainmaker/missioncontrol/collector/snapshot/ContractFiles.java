package se.rainmaker.missioncontrol.collector.snapshot;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Loads the shared contract files from {@code apps/mission-control/contract} for tests. */
public final class ContractFiles {

  private static final Path CONTRACT = Path.of("..", "contract");
  private static final JsonMapper PLAIN_JSON = JsonMapper.builder().build();

  private ContractFiles() {}

  /** The snapshot JSON Schema, with format checks (date-time, date, uri) switched on. */
  public static Schema schema() {
    return load("snapshot.schema.json");
  }

  /** The {@code projects.json} JSON Schema (#99). */
  public static Schema projectsSchema() {
    return load("projects.schema.json");
  }

  /** The {@code projects.json} JSON Schema as a tree, to read its id pattern. */
  public static ObjectNode projectsSchemaTree() {
    return (ObjectNode) PLAIN_JSON.readTree(read("projects.schema.json"));
  }

  /** The sample {@code projects.json} as text. */
  public static String projectsSampleJson() {
    return read("fixtures/projects.sample.json");
  }

  /** A fresh, editable copy of the sample {@code projects.json}. */
  public static ObjectNode projectsSampleTree() {
    return (ObjectNode) PLAIN_JSON.readTree(projectsSampleJson());
  }

  private static Schema load(String file) {
    SchemaRegistry registry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder ->
                builder.schemaRegistryConfig(
                    SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
    return registry.getSchema(read(file), InputFormat.JSON);
  }

  /** The snapshot JSON Schema as a tree, to read its enum lists. */
  public static ObjectNode schemaTree() {
    return (ObjectNode) PLAIN_JSON.readTree(read("snapshot.schema.json"));
  }

  /** The fictional sample snapshot as text. */
  public static String sampleJson() {
    return read("fixtures/snapshot.sample.json");
  }

  /** A fresh, editable copy of the sample snapshot. */
  public static ObjectNode sampleTree() {
    return (ObjectNode) PLAIN_JSON.readTree(sampleJson());
  }

  private static String read(String file) {
    try {
      return Files.readString(CONTRACT.resolve(file));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read contract file " + file, e);
    }
  }
}
