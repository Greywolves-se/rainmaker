package se.rainmaker.missioncontrol.collector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.report.StartHereReport;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotWriter;
import se.rainmaker.missioncontrol.collector.snapshot.Source;

/**
 * Runs one collector run: for each project, all sources, then its snapshot file and START HERE
 * report. Every log line of the run carries the same {@code correlation_id}, so the lines of one
 * run can be found together.
 */
@Component
class CollectorRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

  private final CollectorPipeline pipeline;
  private final SnapshotValidator validator;
  private final SnapshotWriter writer;
  private final CollectorProperties properties;
  private final StartHereReport startHere;

  CollectorRunner(
      CollectorPipeline pipeline,
      SnapshotValidator validator,
      SnapshotWriter writer,
      CollectorProperties properties,
      StartHereReport startHere) {
    this.pipeline = pipeline;
    this.validator = validator;
    this.writer = writer;
    this.properties = properties;
    this.startHere = startHere;
  }

  /**
   * Collects every project. A project that fails is logged and keeps its previous files; the others
   * are still written. The run then fails at the end, so the failure is not missed (CI does not
   * deploy a half-updated set of snapshots).
   */
  @Override
  public void run(String... args) {
    try (MDC.MDCCloseable correlationId =
        MDC.putCloseable("correlation_id", UUID.randomUUID().toString())) {
      log.info("collector started");
      List<String> failedProjects = new ArrayList<>();
      for (ProjectSettings project : properties.projects()) {
        try {
          collect(project);
        } catch (RuntimeException e) {
          // Isolation boundary, like a failing source: one project must not stop the others.
          log.atError()
              .addKeyValue("project", project.id())
              .setCause(e)
              .log("project failed; its previous snapshot stays");
          failedProjects.add(project.id());
        }
      }
      log.atInfo()
          .addKeyValue("projects", properties.projects().size())
          .addKeyValue("failedProjects", failedProjects)
          .log("collector finished");
      if (!failedProjects.isEmpty()) {
        throw new IllegalStateException("Projects failed: " + failedProjects + "; see the log");
      }
    }
  }

  private void collect(ProjectSettings project) {
    Snapshot snapshot = pipeline.run(project);
    // Last line of defence: every source's data was already checked, so this only fails on a bug
    // in the pipeline itself. Nothing is written then, and the previous snapshot stays.
    List<String> problems = validator.problems(snapshot);
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Snapshot does not match the contract: " + problems);
    }
    Path output = properties.snapshotFile(project);
    writer.write(snapshot, output);
    String report = writeStartHere(snapshot, project, output);
    if (project.id().equals(properties.legacySnapshotProject())) {
      // Where the web app reads both files today (#17, #26), until it reads per project (#100).
      Path legacyOutput = properties.outputFolder().resolve("snapshot.json");
      writer.write(snapshot, legacyOutput);
      writeStartHere(snapshot, project, legacyOutput);
    }
    log.atInfo()
        .addKeyValue("project", project.id())
        .addKeyValue("output", output.toString())
        .addKeyValue("report", report)
        .addKeyValue("sources", snapshot.sources().size())
        .addKeyValue("failedSources", countFailed(snapshot))
        .log("snapshot written");
  }

  /**
   * The START HERE report is a side product: if it fails, the error is logged and the project still
   * succeeds, because the snapshot (what staging needs) is already written. Returns the report
   * path, or "failed".
   */
  private String writeStartHere(Snapshot snapshot, ProjectSettings project, Path output) {
    try {
      return startHere.write(snapshot, project, output).toString();
    } catch (RuntimeException e) {
      // Isolation boundary, like a failing source: a bug in the report must not fail the run.
      log.atError()
          .addKeyValue("project", project.id())
          .setCause(e)
          .log("START HERE report failed; the snapshot was written");
      return "failed";
    }
  }

  private static long countFailed(Snapshot snapshot) {
    return snapshot.sources().stream()
        .filter(source -> source.status() == Source.Status.ERROR)
        .count();
  }
}
