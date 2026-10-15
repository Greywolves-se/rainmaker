package se.rainmaker.missioncontrol.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class CollectorApplicationTest {

  @Test
  void logsExactlyOneJsonLineWithCorrelationIdOnStart(CapturedOutput output) {
    try (ConfigurableApplicationContext context =
        SpringApplication.run(CollectorApplication.class)) {
      List<String> lines = output.getOut().lines().filter(line -> !line.isBlank()).toList();

      assertThat(lines).hasSize(1);
      assertThat(lines.getFirst())
          .startsWith("{")
          .endsWith("}")
          .contains("\"message\":\"collector started\"")
          .containsPattern("\"correlation_id\":\"[0-9a-f-]{36}\"");
    }
  }

  @Test
  void runsWithoutWebServer() {
    try (ConfigurableApplicationContext context =
        SpringApplication.run(CollectorApplication.class)) {
      assertThat(context.getEnvironment().getProperty("spring.main.web-application-type"))
          .isEqualTo("none");
    }
  }

  @Test
  void exitCodeIsZeroAfterRun() {
    ConfigurableApplicationContext context = SpringApplication.run(CollectorApplication.class);

    assertThat(SpringApplication.exit(context)).isZero();
  }
}
