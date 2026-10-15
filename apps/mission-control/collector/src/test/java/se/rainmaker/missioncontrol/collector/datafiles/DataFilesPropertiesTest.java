package se.rainmaker.missioncontrol.collector.datafiles;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import se.rainmaker.missioncontrol.collector.CollectorApplication;

class DataFilesPropertiesTest {

  @TempDir Path folder;

  @Test
  void linksPointToTheCommitThatWasReadWhenGithubShaIsSet() {
    try (ConfigurableApplicationContext context =
        SpringApplication.run(
            CollectorApplication.class,
            "--GITHUB_SHA=abc1234",
            "--mission-control.collector.output=" + folder.resolve("snapshot.json"),
            "--mission-control.collector.data-files.folder=" + folder,
            "--mission-control.collector.sources.github.enabled=false")) {
      assertThat(context.getBean(DataFilesProperties.class).filesUrl())
          .isEqualTo(
              "https://github.com/Greywolves-se/rainmaker/blob/abc1234/apps/mission-control/data");
    }
  }
}
