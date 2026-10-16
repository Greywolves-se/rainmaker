package se.rainmaker.missioncontrol.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/**
 * Acceptance test for #11 and #99: a full run with the real project settings and two fake sources,
 * one of which fails.
 */
@SpringBootTest
class CollectorRunTest {

  @TempDir static Path folder;

  @DynamicPropertySource
  static void writeToTemporaryFolder(DynamicPropertyRegistry registry) {
    registry.add("mission-control.collector.output-folder", () -> folder.toString());
    // The real data-file source (#15) is part of every run; an empty folder keeps it predictable.
    registry.add(
        "mission-control.collector.data-files.folder", () -> folder.resolve("data").toString());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"rainmaker, Rainmaker", "mission-control, Mission Control"})
  void writesOneSnapshotPerProjectWithOneOkAndOneFailedSource(String projectId, String name) {
    Path output = folder.resolve(projectId).resolve("snapshot.json");

    Snapshot snapshot = SnapshotJson.mapper().readValue(output.toFile(), Snapshot.class);

    assertThat(snapshot.project().name()).isEqualTo(name);
    assertThat(snapshot.sources())
        .extracting(Source::id, Source::status)
        .containsExactly(
            tuple("broken", Source.Status.ERROR),
            tuple("data-files", Source.Status.OK),
            tuple("fake-ok", Source.Status.OK),
            tuple("github", Source.Status.DISABLED),
            tuple("github-pull-requests", Source.Status.DISABLED));
    assertThat(ContractFiles.schema().validate(SnapshotJson.mapper().readTree(output.toFile())))
        .isEmpty();
    // The START HERE report (#25) is written next to each snapshot in the same run.
    assertThat(folder.resolve(projectId).resolve("start-here.md")).isNotEmptyFile();
  }

  @Test
  void missionControlIsAlsoWrittenWhereTheWebAppReadsItToday() throws IOException {
    assertThat(Files.readString(folder.resolve("snapshot.json")))
        .isEqualTo(Files.readString(folder.resolve("mission-control").resolve("snapshot.json")));
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
