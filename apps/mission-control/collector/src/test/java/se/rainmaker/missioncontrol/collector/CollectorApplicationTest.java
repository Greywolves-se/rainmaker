package se.rainmaker.missioncontrol.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class CollectorApplicationTest {

  private static final Pattern CORRELATION_ID =
      Pattern.compile("\"correlation_id\":\"([0-9a-f-]{36})\"");

  @TempDir Path folder;

  @Test
  void logsOnlyJsonLinesThatShareOneCorrelationId(CapturedOutput output) {
    try (ConfigurableApplicationContext context = run()) {
      List<String> lines = output.getOut().lines().filter(line -> !line.isBlank()).toList();

      assertThat(lines).isNotEmpty().allMatch(line -> line.startsWith("{") && line.endsWith("}"));
      assertThat(lines.getFirst()).contains("\"message\":\"collector started\"");
      assertThat(lines.getLast()).contains("\"message\":\"collector finished\"");
      assertThat(lines.stream().map(CollectorApplicationTest::correlationId).distinct())
          .singleElement()
          .isNotNull();
    }
  }

  @Test
  void runsWithoutWebServer() {
    try (ConfigurableApplicationContext context = run()) {
      assertThat(context.getEnvironment().getProperty("spring.main.web-application-type"))
          .isEqualTo("none");
    }
  }

  @Test
  void exitCodeIsZeroAfterRun() {
    ConfigurableApplicationContext context = run();

    assertThat(SpringApplication.exit(context)).isZero();
  }

  private ConfigurableApplicationContext run() {
    return SpringApplication.run(
        CollectorApplication.class, "--mission-control.collector.output-folder=" + folder);
  }

  private static String correlationId(String line) {
    Matcher matcher = CORRELATION_ID.matcher(line);
    return matcher.find() ? matcher.group(1) : null;
  }
}
