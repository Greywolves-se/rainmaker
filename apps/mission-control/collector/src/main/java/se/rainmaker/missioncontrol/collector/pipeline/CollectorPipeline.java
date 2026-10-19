package se.rainmaker.missioncontrol.collector.pipeline;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.rainmaker.missioncontrol.collector.snapshot.Project;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.Source;

/**
 * Runs every enabled source, in id order, and assembles one project's snapshot. A source that
 * fails, delivers data that breaks the contract, or clashes with an earlier source is marked as
 * {@code error} in {@code sources[]} and adds nothing; the other sources are not affected.
 */
@Service
public class CollectorPipeline {

  /** Same pattern as {@code sourceId} in the contract schema; a test keeps them equal. */
  static final Pattern SOURCE_ID = Pattern.compile("^[a-z][a-z0-9-]*$");

  private static final Logger log = LoggerFactory.getLogger(CollectorPipeline.class);
  private static final int MAX_PROBLEMS_SHOWN = 3;

  private final List<SourceCollector> sources;
  private final CollectorProperties properties;
  private final SnapshotValidator validator;
  private final Clock clock;

  /**
   * Fails at startup if two sources share an id, an id breaks the contract's pattern, or a source
   * has no name.
   */
  public CollectorPipeline(
      List<SourceCollector> sources,
      CollectorProperties properties,
      SnapshotValidator validator,
      Clock clock) {
    this.sources = sources.stream().sorted(Comparator.comparing(SourceCollector::id)).toList();
    this.properties = properties;
    this.validator = validator;
    this.clock = clock;
    checkSourceIds(this.sources);
    warnAboutSettingsForUnknownSources();
  }

  /** Runs all sources once for {@code project} and returns its snapshot. */
  public Snapshot run(ProjectSettings project) {
    SnapshotBuilder snapshot = new SnapshotBuilder();
    List<Source> results = new ArrayList<>();
    for (SourceCollector source : sources) {
      if (!properties.isEnabled(source.id())) {
        results.add(result(source, Source.Status.DISABLED, "Disabled in configuration"));
        continue;
      }
      Optional<String> notSetUp = source.notSetUpFor(project);
      results.add(
          notSetUp.isPresent()
              ? result(source, Source.Status.DISABLED, notSetUp.get())
              : collect(source, project, snapshot));
    }
    return snapshot.build(clock.instant(), fallbackProject(project), results);
  }

  private Source collect(
      SourceCollector source, ProjectSettings project, SnapshotBuilder snapshot) {
    SnapshotBuilder contribution = new SnapshotBuilder();
    try {
      source.collect(project, contribution);
    } catch (SourceException e) {
      log.atWarn()
          .addKeyValue("project", project.id())
          .addKeyValue("source", source.id())
          .setCause(e)
          .log("source failed");
      String message = e.getMessage();
      return result(
          source,
          Source.Status.ERROR,
          message == null || message.isBlank() ? "Source failed" : message);
    } catch (RuntimeException e) {
      // Isolation boundary: a bug in one source must not stop the others. The message may hold
      // details we cannot vet, so only the exception type is shown; the full error is logged.
      // Errors (e.g. OutOfMemoryError) are not caught: the JVM itself is in trouble then.
      log.atError()
          .addKeyValue("project", project.id())
          .addKeyValue("source", source.id())
          .setCause(e)
          .log("source failed unexpectedly");
      return result(
          source,
          Source.Status.ERROR,
          "Unexpected error (" + e.getClass().getSimpleName() + "); see the collector log");
    }
    List<String> problems = problemsWith(contribution, project, snapshot);
    if (!problems.isEmpty()) {
      log.atWarn()
          .addKeyValue("project", project.id())
          .addKeyValue("source", source.id())
          .addKeyValue("problems", problems)
          .log("source data rejected");
      return result(source, Source.Status.ERROR, summary(problems));
    }
    snapshot.add(contribution);
    log.atInfo()
        .addKeyValue("project", project.id())
        .addKeyValue("source", source.id())
        .log("source collected");
    return new Source(source.id(), source.name(), Source.Status.OK, clock.instant(), null);
  }

  /** Contract violations first (e.g. a required field is null), then clashes with other sources. */
  private List<String> problemsWith(
      SnapshotBuilder contribution, ProjectSettings project, SnapshotBuilder snapshot) {
    List<String> invalid =
        validator.problems(
            contribution.build(clock.instant(), fallbackProject(project), List.of()));
    if (!invalid.isEmpty()) {
      return invalid.stream().map(problem -> "invalid data at " + problem).toList();
    }
    return snapshot.conflictsWith(contribution);
  }

  private static String summary(List<String> problems) {
    String shown = String.join("; ", problems.stream().limit(MAX_PROBLEMS_SHOWN).toList());
    int more = problems.size() - MAX_PROBLEMS_SHOWN;
    return more > 0 ? shown + " (and " + more + " more)" : shown;
  }

  private static Project fallbackProject(ProjectSettings project) {
    return new Project(project.name(), List.of());
  }

  private static Source result(SourceCollector source, Source.Status status, String message) {
    return new Source(source.id(), source.name(), status, null, message);
  }

  private static void checkSourceIds(List<SourceCollector> sources) {
    Set<String> seen = new HashSet<>();
    for (SourceCollector source : sources) {
      if (!SOURCE_ID.matcher(source.id()).matches()) {
        throw new IllegalStateException(
            "Source id '" + source.id() + "' must match " + SOURCE_ID.pattern());
      }
      if (!seen.add(source.id())) {
        throw new IllegalStateException("Two sources have the id '" + source.id() + "'");
      }
      if (source.name() == null || source.name().isBlank()) {
        throw new IllegalStateException("Source '" + source.id() + "' has no name");
      }
    }
  }

  private void warnAboutSettingsForUnknownSources() {
    Set<String> unknown = new TreeSet<>(properties.sources().keySet());
    sources.forEach(source -> unknown.remove(source.id()));
    if (!unknown.isEmpty()) {
      log.atWarn()
          .addKeyValue("unknownSourceIds", unknown)
          .log("settings name sources that do not exist; check the spelling");
    }
  }
}
