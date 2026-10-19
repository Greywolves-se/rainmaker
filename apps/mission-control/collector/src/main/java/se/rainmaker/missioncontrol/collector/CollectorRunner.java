package se.rainmaker.missioncontrol.collector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.navigator.NavigatorStep;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.report.StartHereReport;
import se.rainmaker.missioncontrol.collector.snapshot.ProjectList;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotWriter;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import tools.jackson.core.JacksonException;

/**
 * Runs one collector run: for each project, all sources, then its snapshot file and START HERE
 * report. Every log line of the run carries the same {@code correlation_id}, so the lines of one
 * run can be found together.
 */
@Component
class CollectorRunner implements CommandLineRunner {

  static final String PROJECT_LIST_FILE = "projects.json";

  private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

  private final CollectorPipeline pipeline;
  private final SnapshotValidator validator;
  private final SnapshotWriter writer;
  private final CollectorProperties properties;
  private final StartHereReport startHere;
  private final NavigatorStep navigatorStep;

  CollectorRunner(
      CollectorPipeline pipeline,
      SnapshotValidator validator,
      SnapshotWriter writer,
      CollectorProperties properties,
      StartHereReport startHere,
      NavigatorStep navigatorStep) {
    this.pipeline = pipeline;
    this.validator = validator;
    this.writer = writer;
    this.properties = properties;
    this.startHere = startHere;
    this.navigatorStep = navigatorStep;
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
      writeProjectList();
      log.atInfo()
          .addKeyValue("projects", properties.projects().size())
          .addKeyValue("failedProjects", failedProjects)
          .log("collector finished");
      if (!failedProjects.isEmpty()) {
        throw new IllegalStateException("Projects failed: " + failedProjects + "; see the log");
      }
    }
  }

  /**
   * Writes {@code projects.json} after all projects ran, listing only projects that have a
   * snapshot: this run's, or the previous one when the project failed. The name comes from that
   * snapshot, so the list and the pages always agree. With no snapshot at all, the file is not
   * written and the previous one stays.
   */
  private void writeProjectList() {
    List<ProjectList.Entry> entries = new ArrayList<>();
    for (ProjectSettings project : properties.projects()) {
      projectName(properties.snapshotFile(project))
          .ifPresent(name -> entries.add(new ProjectList.Entry(project.id(), name)));
    }
    if (entries.isEmpty()) {
      log.atWarn().log("no project has a snapshot; projects.json was not written");
      return;
    }
    writer.write(
        new ProjectList(ProjectList.SCHEMA_VERSION, entries),
        properties.outputFolder().resolve(PROJECT_LIST_FILE));
  }

  /** The project name in a snapshot file; empty when there is no file or it cannot be read. */
  private static Optional<String> projectName(Path snapshotFile) {
    if (!Files.exists(snapshotFile)) {
      return Optional.empty();
    }
    try {
      String name =
          SnapshotJson.mapper().readTree(snapshotFile.toFile()).at("/project/name").asString("");
      return name.isBlank() ? Optional.empty() : Optional.of(name);
    } catch (JacksonException e) {
      // A broken file would fail in the web app too, so the project is left out of the list.
      log.atError()
          .addKeyValue("file", snapshotFile.toString())
          .setCause(e)
          .log("snapshot cannot be read; project left out of projects.json");
      return Optional.empty();
    }
  }

  private void collect(ProjectSettings project) {
    // The AI step runs after all sources and never throws: it reports itself in sources[] (#115).
    Snapshot snapshot = navigatorStep.addTo(pipeline.run(project));
    // Last line of defence: every source's data was already checked, so this only fails on a bug
    // in the pipeline itself. Nothing is written then, and the previous snapshot stays.
    List<String> problems = validator.problems(snapshot);
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Snapshot does not match the contract: " + problems);
    }
    Path output = properties.snapshotFile(project);
    writer.write(snapshot, output);
    String report = writeStartHere(snapshot, project, output);
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
