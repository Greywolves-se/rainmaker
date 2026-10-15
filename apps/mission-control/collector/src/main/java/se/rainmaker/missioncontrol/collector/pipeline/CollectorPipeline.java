package se.rainmaker.missioncontrol.collector.pipeline;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.rainmaker.missioncontrol.collector.snapshot.Project;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.Source;

/**
 * Runs every enabled source, in id order, and assembles one snapshot. A source that fails is marked
 * as {@code error} in {@code sources[]} and adds nothing; the other sources are not affected.
 */
@Service
public class CollectorPipeline {

  private static final Logger log = LoggerFactory.getLogger(CollectorPipeline.class);
  private static final Pattern SOURCE_ID = Pattern.compile("^[a-z][a-z0-9-]*$");

  private final List<SourceCollector> sources;
  private final CollectorProperties properties;
  private final Clock clock;

  /** Fails at startup if two sources share an id or an id breaks the contract's pattern. */
  public CollectorPipeline(
      List<SourceCollector> sources, CollectorProperties properties, Clock clock) {
    this.sources = sources.stream().sorted(Comparator.comparing(SourceCollector::id)).toList();
    this.properties = properties;
    this.clock = clock;
    checkSourceIds(this.sources);
  }

  /** Runs all sources once and returns the snapshot. */
  public Snapshot run() {
    SnapshotBuilder snapshot = new SnapshotBuilder();
    List<Source> results = new ArrayList<>();
    for (SourceCollector source : sources) {
      results.add(
          properties.isEnabled(source.id())
              ? collect(source, snapshot)
              : result(source, Source.Status.DISABLED, "Disabled in configuration"));
    }
    Project fallbackProject = new Project(properties.projectName(), List.of());
    return snapshot.build(clock.instant(), fallbackProject, results);
  }

  private Source collect(SourceCollector source, SnapshotBuilder snapshot) {
    SnapshotBuilder contribution = new SnapshotBuilder();
    try {
      source.collect(contribution);
    } catch (SourceException e) {
      log.atWarn().addKeyValue("source", source.id()).setCause(e).log("source failed");
      return result(source, Source.Status.ERROR, e.getMessage());
    } catch (RuntimeException e) {
      // Isolation boundary: a bug in one source must not stop the others. The message may hold
      // details we cannot vet, so only the exception type is shown; the full error is logged.
      log.atError()
          .addKeyValue("source", source.id())
          .setCause(e)
          .log("source failed unexpectedly");
      return result(
          source,
          Source.Status.ERROR,
          "Unexpected error (" + e.getClass().getSimpleName() + "); see the collector log");
    }
    List<String> conflicts = snapshot.conflictsWith(contribution);
    if (!conflicts.isEmpty()) {
      log.atWarn()
          .addKeyValue("source", source.id())
          .addKeyValue("conflicts", conflicts)
          .log("source data conflicts with another source");
      return result(source, Source.Status.ERROR, String.join("; ", conflicts));
    }
    snapshot.add(contribution);
    log.atInfo().addKeyValue("source", source.id()).log("source collected");
    return new Source(source.id(), source.name(), Source.Status.OK, clock.instant(), null);
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
    }
  }
}
