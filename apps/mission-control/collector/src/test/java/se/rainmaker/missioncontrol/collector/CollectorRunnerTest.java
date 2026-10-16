package se.rainmaker.missioncontrol.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.report.StartHereReport;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotWriter;

@ExtendWith(OutputCaptureExtension.class)
class CollectorRunnerTest {

  @TempDir Path folder;

  @Test
  void failingReportOnlyLogsAndTheSnapshotIsStillWritten(CapturedOutput output) {
    Path snapshotFile = folder.resolve("snapshot.json");
    CollectorPipeline pipeline = mock(CollectorPipeline.class);
    when(pipeline.run())
        .thenReturn(SnapshotJson.mapper().readValue(ContractFiles.sampleJson(), Snapshot.class));
    StartHereReport report = mock(StartHereReport.class);
    when(report.write(any(), any())).thenThrow(new IllegalStateException("report bug"));
    CollectorRunner runner =
        new CollectorRunner(
            pipeline,
            new SnapshotValidator(),
            new SnapshotWriter(),
            new CollectorProperties("Rainmaker", snapshotFile, Map.of()),
            report);

    assertThatCode(runner::run).doesNotThrowAnyException();

    assertThat(snapshotFile).isNotEmptyFile();
    assertThat(output.getOut())
        .contains("START HERE report failed")
        .contains("report bug")
        .contains("snapshot written");
  }
}
