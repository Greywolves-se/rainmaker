package se.rainmaker.missioncontrol.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.rainmaker.missioncontrol.collector.pipeline.FakeSource;
import se.rainmaker.missioncontrol.collector.pipeline.SourceCollector;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;
import se.rainmaker.missioncontrol.collector.snapshot.Source;

/** Acceptance test for #11: a full run with two fake sources, one of which fails. */
@SpringBootTest
class CollectorRunTest {

  @TempDir static Path folder;

  @DynamicPropertySource
  static void writeToTemporaryFolder(DynamicPropertyRegistry registry) {
    registry.add(
        "mission-control.collector.output", () -> folder.resolve("snapshot.json").toString());
    // The real data-file source (#15) is part of every run; an empty folder keeps it predictable.
    registry.add(
        "mission-control.collector.data-files.folder", () -> folder.resolve("data").toString());
  }

  @Test
  void writesSnapshotWithOneOkAndOneFailedSource() {
    Path output = folder.resolve("snapshot.json");

    Snapshot snapshot = SnapshotJson.mapper().readValue(output.toFile(), Snapshot.class);

    assertThat(snapshot.sources())
        .extracting(Source::id, Source::status)
        .containsExactly(
            tuple("broken", Source.Status.ERROR),
            tuple("data-files", Source.Status.OK),
            tuple("fake-ok", Source.Status.OK),
            tuple("github", Source.Status.DISABLED));
    assertThat(ContractFiles.schema().validate(SnapshotJson.mapper().readTree(output.toFile())))
        .isEmpty();
    // The START HERE report (#25) is written next to the snapshot in the same run.
    assertThat(folder.resolve("start-here.md")).isNotEmptyFile();
  }

  @TestConfiguration
  static class TwoFakeSources {

    @Bean
    SourceCollector fakeOk() {
      return FakeSource.withId("fake-ok").withWorkItem("1");
    }

    @Bean
    SourceCollector broken() {
      return FakeSource.withId("broken").failingWith(new IllegalStateException("boom"));
    }
  }
}
