package se.rainmaker.missioncontrol.collector;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runs one collector run. Every log line of the run carries the same {@code correlation_id}, so the
 * lines of one run can be found together.
 */
@Component
class CollectorRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

  @Override
  public void run(String... args) {
    try (MDC.MDCCloseable correlationId =
        MDC.putCloseable("correlation_id", UUID.randomUUID().toString())) {
      log.info("collector started");
    }
  }
}
