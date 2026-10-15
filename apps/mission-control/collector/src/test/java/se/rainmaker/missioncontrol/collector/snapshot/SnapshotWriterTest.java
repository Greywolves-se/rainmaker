package se.rainmaker.missioncontrol.collector.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotWriterTest {

  private final SnapshotWriter writer = new SnapshotWriter();

  @Test
  void writesSnapshotThatMatchesSchemaAndCreatesFolders(@TempDir Path folder) throws IOException {
    Path output = folder.resolve("out/nested/snapshot.json");

    writer.write(sample(), output);

    assertThat(ContractFiles.schema().validate(SnapshotJson.mapper().readTree(output.toFile())))
        .isEmpty();
  }

  @Test
  void replacesThePreviousSnapshotAndLeavesNoTemporaryFiles(@TempDir Path folder)
      throws IOException {
    Path output = folder.resolve("snapshot.json");
    Files.writeString(output, "old");

    writer.write(sample(), output);

    assertThat(Files.readString(output)).startsWith("{");
    try (Stream<Path> files = Files.list(folder)) {
      assertThat(files).containsExactly(output);
    }
  }

  private static Snapshot sample() {
    return SnapshotJson.mapper().readValue(ContractFiles.sampleJson(), Snapshot.class);
  }
}
