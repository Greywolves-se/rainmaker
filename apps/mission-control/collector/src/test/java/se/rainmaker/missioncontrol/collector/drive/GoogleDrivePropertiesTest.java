package se.rainmaker.missioncontrol.collector.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoogleDrivePropertiesTest {

  @ParameterizedTest
  @ValueSource(strings = {"abc' or name contains 'x", "a b", "a/b", ""})
  void folderIdThatCouldChangeTheQueryIsRejected(String folder) {
    assertThatThrownBy(() -> properties(Map.of("rainmaker", folder)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a valid Drive id");
  }

  @Test
  void realLookingFolderIdIsAccepted() {
    assertThat(properties(Map.of("rainmaker", "1zh8BBu_31YV7W2a0N_CELxMNXDOf0vBG")).folders())
        .containsEntry("rainmaker", "1zh8BBu_31YV7W2a0N_CELxMNXDOf0vBG");
  }

  private static GoogleDriveProperties properties(Map<String, String> folders) {
    return new GoogleDriveProperties(
        URI.create("https://www.googleapis.com/drive/v3"),
        folders,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        1,
        Duration.ofMillis(1));
  }
}
