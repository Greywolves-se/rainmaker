package se.rainmaker.missioncontrol.collector.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GithubPropertiesTest {

  @Test
  void toStringNeverShowsTheToken() {
    assertThat(properties("ghp_secret", List.of("example-org/repo")).toString())
        .doesNotContain("ghp_secret")
        .contains("token=***");
  }

  @Test
  void refusesRepositoryThatIsNotOwnerSlashName() {
    assertThatThrownBy(() -> properties("t", List.of("example-org/repo/../../user")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("owner/name");
  }

  @Test
  void refusesWorkflowThatIsNotFileName() {
    assertThatThrownBy(() -> withWorkflows(Map.of("rainmaker", List.of("../ci.yml"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rainmaker")
        .hasMessageContaining("../ci.yml");
  }

  @Test
  void projectWithoutWorkflowsHasNone() {
    GithubProperties properties = withWorkflows(Map.of("mission-control", List.of("ci.yml")));

    assertThat(properties.workflowsFor("mission-control")).containsExactly("ci.yml");
    assertThat(properties.workflowsFor("rainmaker")).isEmpty();
  }

  @Test
  void workflowsBindFromCommaSeparatedListPerProject() {
    Map<String, String> settings =
        Map.of(
            "mission-control.collector.github.api-url", "https://api.github.com",
            "mission-control.collector.github.connect-timeout", "5s",
            "mission-control.collector.github.read-timeout", "30s",
            "mission-control.collector.github.max-attempts", "3",
            "mission-control.collector.github.backoff", "1s",
            "mission-control.collector.github.rate-limit-max-wait", "60s",
            "mission-control.collector.github.max-pages", "50",
            "mission-control.collector.github.workflows.mission-control",
                "mission-control-ci.yml,docker.yml");

    GithubProperties properties =
        new Binder(new MapConfigurationPropertySource(settings))
            .bind("mission-control.collector.github", GithubProperties.class)
            .get();

    assertThat(properties.workflowsFor("mission-control"))
        .containsExactly("mission-control-ci.yml", "docker.yml");
  }

  private static GithubProperties properties(String token, List<String> repositories) {
    return new GithubProperties(
        URI.create("https://api.github.com"),
        token,
        repositories,
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        3,
        Duration.ofSeconds(1),
        Duration.ofSeconds(60),
        50,
        Map.of());
  }

  private static GithubProperties withWorkflows(Map<String, List<String>> workflows) {
    return new GithubProperties(
        URI.create("https://api.github.com"),
        "t",
        List.of("example-org/repo"),
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        3,
        Duration.ofSeconds(1),
        Duration.ofSeconds(60),
        50,
        workflows);
  }
}
