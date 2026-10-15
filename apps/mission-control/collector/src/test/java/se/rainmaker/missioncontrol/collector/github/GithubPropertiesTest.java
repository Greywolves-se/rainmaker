package se.rainmaker.missioncontrol.collector.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

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
        50);
  }
}
