package se.rainmaker.missioncontrol.collector;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotWriter;
import se.rainmaker.missioncontrol.collector.snapshot.Source;

/**
 * Runs one collector run: all sources, then the snapshot file. Every log line of the run carries
 * the same {@code correlation_id}, so the lines of one run can be found together.
 */
@Component
class CollectorRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

  private final CollectorPipeline pipeline;
  private final SnapshotWriter writer;
  private final CollectorProperties properties;

  CollectorRunner(
      CollectorPipeline pipeline, SnapshotWriter writer, CollectorProperties properties) {
    this.pipeline = pipeline;
    this.writer = writer;
    this.properties = properties;
  }

  @Override
  public void run(String... args) {
    try (MDC.MDCCloseable correlationId =
        MDC.putCloseable("correlation_id", UUID.randomUUID().toString())) {
      log.info("collector started");
      Snapshot snapshot = pipeline.run();
      writer.write(snapshot, properties.output());
      log.atInfo()
          .addKeyValue("output", properties.output().toString())
          .addKeyValue("sources", snapshot.sources().size())
          .addKeyValue("failedSources", countFailed(snapshot))
          .log("snapshot written");
    }
  }

  private static long countFailed(Snapshot snapshot) {
    return snapshot.sources().stream()
        .filter(source -> source.status() == Source.Status.ERROR)
        .count();
  }
}
