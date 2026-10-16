package se.rainmaker.missioncontrol.collector.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.rainmaker.missioncontrol.collector.datafiles.DataFilesProperties;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;

class StartHereReportTest {

  @TempDir Path data;
  @TempDir Path out;

  @Test
  void writesTheReportNextToTheSnapshotWithTheIntro() throws IOException {
    Files.writeString(data.resolve("start-here-intro.md"), "## What we build\nNews widgets.\n");

    Path report = report().write(sample(), out.resolve("snapshot.json"));

    assertThat(report).isEqualTo(out.resolve("start-here.md"));
    assertThat(Files.readString(report))
        .startsWith("# START HERE – Example project")
        .contains("## What we build\nNews widgets.");
  }

  @Test
  void missingIntroGivesHint() throws IOException {
    Path report = report().write(sample(), out.resolve("snapshot.json"));

    assertThat(Files.readString(report)).contains(StartHereReport.MISSING_INTRO);
  }

  @Test
  void theRealIntroExists() {
    assertThat(Path.of("..", "data", "start-here-intro.md")).isNotEmptyFile();
  }

  private StartHereReport report() {
    return new StartHereReport(new DataFilesProperties(data, "https://example.org/data"));
  }

  private static Snapshot sample() {
    return SnapshotJson.mapper().readValue(ContractFiles.sampleJson(), Snapshot.class);
  }
}
