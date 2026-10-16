package se.rainmaker.missioncontrol.collector.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.rainmaker.missioncontrol.collector.datafiles.DataFilesProperties;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;

class StartHereReportTest {

  private static final ProjectSettings PROJECT =
      new ProjectSettings("example", "Example project", null, null);

  @TempDir Path data;
  @TempDir Path out;

  @Test
  void writesTheReportNextToTheSnapshotWithTheProjectsIntro() throws IOException {
    Files.createDirectories(data.resolve("example"));
    Files.writeString(
        data.resolve("example").resolve("start-here-intro.md"),
        "## What we build\nNews widgets.\n");
    Files.writeString(data.resolve("start-here-intro.md"), "Intro of no project\n");

    Path report = report().write(sample(), PROJECT, out.resolve("snapshot.json"));

    assertThat(report).isEqualTo(out.resolve("start-here.md"));
    assertThat(Files.readString(report))
        .startsWith("# START HERE – Example project")
        .contains("## What we build\nNews widgets.")
        .doesNotContain("Intro of no project");
  }

  @Test
  void missingIntroGivesHint() throws IOException {
    Path report = report().write(sample(), PROJECT, out.resolve("snapshot.json"));

    assertThat(Files.readString(report)).contains(StartHereReport.MISSING_INTRO);
  }

  @Test
  void theRealIntrosExist() {
    assertThat(Path.of("..", "data", "rainmaker", "start-here-intro.md")).isNotEmptyFile();
    assertThat(Path.of("..", "data", "mission-control", "start-here-intro.md")).isNotEmptyFile();
  }

  private StartHereReport report() {
    return new StartHereReport(new DataFilesProperties(data, "https://example.org/data"));
  }

  private static Snapshot sample() {
    return SnapshotJson.mapper().readValue(ContractFiles.sampleJson(), Snapshot.class);
  }
}
