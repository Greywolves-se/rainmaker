package se.rainmaker.missioncontrol.collector.snapshot;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Checks snapshots against the contract schema ({@code contract/snapshot.schema.json}, packed into
 * the jar), so the collector never writes data the dashboard would reject.
 */
@Component
public class SnapshotValidator {

  private static final String SCHEMA_RESOURCE = "contract/snapshot.schema.json";

  private final Schema schema;

  /** Loads the schema from the classpath; fails at startup if it is missing. */
  public SnapshotValidator() {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(SCHEMA_RESOURCE + " is not on the classpath");
      }
      SchemaRegistry registry =
          SchemaRegistry.withDefaultDialect(
              SpecificationVersion.DRAFT_2020_12,
              builder ->
                  builder.schemaRegistryConfig(
                      SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
      schema = registry.getSchema(in, InputFormat.JSON);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + SCHEMA_RESOURCE, e);
    }
  }

  /**
   * Returns what is wrong with {@code snapshot}, e.g. {@code /workItems/0/url (format.uri)}; empty
   * when it matches the schema. Problems name the place and the rule, never the value, so they are
   * safe to show on the dashboard.
   */
  public List<String> problems(Snapshot snapshot) {
    return schema.validate(SnapshotJson.mapper().valueToTree(snapshot)).stream()
        .map(error -> error.getInstanceLocation() + " (" + error.getMessageKey() + ")")
        .toList();
  }
}
