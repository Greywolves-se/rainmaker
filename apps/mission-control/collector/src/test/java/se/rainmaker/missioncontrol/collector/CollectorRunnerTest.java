package se.rainmaker.missioncontrol.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.report.StartHereReport;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;
import se.rainmaker.missioncontrol.collector.snapshot.ProjectList;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotWriter;

@ExtendWith(OutputCaptureExtension.class)
class CollectorRunnerTest {

  private static final ProjectSettings RAINMAKER =
      new ProjectSettings("rainmaker", "Rainmaker", null, "mission-control");
  private static final ProjectSettings MISSION_CONTROL =
      new ProjectSettings("mission-control", "Mission Control", "mission-control", null);

  @TempDir Path folder;

  private final CollectorPipeline pipeline = mock(CollectorPipeline.class);
  private final StartHereReport report = mock(StartHereReport.class);

  @Test
  void failingReportOnlyLogsAndTheSnapshotIsStillWritten(CapturedOutput output) {
    when(pipeline.run(any())).thenReturn(sample());
    when(report.write(any(), any(), any())).thenThrow(new IllegalStateException("report bug"));

    assertThatCode(runner()::run).doesNotThrowAnyException();

    assertThat(folder.resolve("rainmaker").resolve("snapshot.json")).isNotEmptyFile();
    assertThat(output.getOut())
        .contains("START HERE report failed")
        .contains("report bug")
        .contains("snapshot written");
  }

  @Test
  void failingProjectDoesNotStopTheOtherButFailsTheRun(CapturedOutput output) {
    when(pipeline.run(RAINMAKER)).thenThrow(new IllegalStateException("pipeline bug"));
    when(pipeline.run(MISSION_CONTROL)).thenReturn(sample());
    when(report.write(any(), any(), any())).thenReturn(folder.resolve("start-here.md"));

    assertThatThrownBy(runner()::run).hasMessageContaining("Projects failed: [rainmaker]");

    assertThat(folder.resolve("rainmaker")).doesNotExist();
    assertThat(folder.resolve("mission-control").resolve("snapshot.json")).isNotEmptyFile();
    assertThat(folder.resolve("snapshot.json")).doesNotExist();
    assertThat(output.getOut()).contains("project failed").contains("pipeline bug");
    // A new project that fails its first run has no snapshot, so it is not listed.
    assertThat(projectList().projects())
        .extracting(ProjectList.Entry::id, ProjectList.Entry::name)
        .containsExactly(tuple("mission-control", sample().project().name()));
  }

  @Test
  void failedProjectStaysListedWithTheNameOfItsPreviousSnapshot() throws IOException {
    writePrevious(
        "rainmaker",
        ContractFiles.sampleJson().replace("\"Example project\"", "\"Rainmaker last run\""));
    when(pipeline.run(RAINMAKER)).thenThrow(new IllegalStateException("pipeline bug"));
    when(pipeline.run(MISSION_CONTROL)).thenReturn(sample());
    when(report.write(any(), any(), any())).thenReturn(folder.resolve("start-here.md"));

    assertThatThrownBy(runner()::run).isInstanceOf(IllegalStateException.class);

    assertThat(projectList().projects())
        .extracting(ProjectList.Entry::id, ProjectList.Entry::name)
        .containsExactly(
            tuple("rainmaker", "Rainmaker last run"),
            tuple("mission-control", sample().project().name()));
  }

  @Test
  void withoutAnyReadableSnapshotTheProjectListIsNotWritten(CapturedOutput output)
      throws IOException {
    writePrevious("rainmaker", "not json");
    when(pipeline.run(any())).thenThrow(new IllegalStateException("pipeline bug"));

    assertThatThrownBy(runner()::run).isInstanceOf(IllegalStateException.class);

    assertThat(folder.resolve("projects.json")).doesNotExist();
    assertThat(output.getOut())
        .contains("snapshot cannot be read")
        .contains("no project has a snapshot");
  }

  private void writePrevious(String projectId, String content) throws IOException {
    Files.createDirectories(folder.resolve(projectId));
    Files.writeString(folder.resolve(projectId).resolve("snapshot.json"), content);
  }

  private ProjectList projectList() {
    return SnapshotJson.mapper()
        .readValue(folder.resolve("projects.json").toFile(), ProjectList.class);
  }

  private CollectorRunner runner() {
    return new CollectorRunner(
        pipeline,
        new SnapshotValidator(),
        new SnapshotWriter(),
        new CollectorProperties(List.of(RAINMAKER, MISSION_CONTROL), folder, Map.of()),
        report);
  }

  private static Snapshot sample() {
    return SnapshotJson.mapper().readValue(ContractFiles.sampleJson(), Snapshot.class);
  }
}
