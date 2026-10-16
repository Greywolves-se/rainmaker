package se.rainmaker.missioncontrol.collector.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;

class ProjectSettingsTest {

  private static final ProjectSettings RAINMAKER =
      new ProjectSettings("rainmaker", "Rainmaker", null, "mission-control");
  private static final ProjectSettings MISSION_CONTROL =
      new ProjectSettings("mission-control", "Mission Control", "mission-control", "");

  @Test
  void labelDecidesTheProjectOfEveryItem() {
    List<List<String>> items =
        List.of(
            List.of(),
            List.of("bug"),
            List.of("mission-control"),
            List.of("bug", "Mission-Control"));

    for (List<String> labels : items) {
      assertThat(RAINMAKER.includes(labels))
          .as("exactly one project includes %s", labels)
          .isNotEqualTo(MISSION_CONTROL.includes(labels));
    }
    assertThat(MISSION_CONTROL.includes(List.of("bug", "Mission-Control"))).isTrue();
    assertThat(RAINMAKER.includes(List.of("bug"))).isTrue();
  }

  @Test
  void projectWithoutLabelRulesIncludesEverything() {
    ProjectSettings all = new ProjectSettings("all", "All", " ", null);

    assertThat(all.includes(List.of())).isTrue();
    assertThat(all.includes(List.of("mission-control"))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Rainmaker", "../out", "a/b", "-x"})
  void idThatIsNoSafeFolderNameIsRejected(String id) {
    assertThatThrownBy(() -> new ProjectSettings(id, "Name", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match");
  }

  @Test
  void idPatternIsTheSameAsInTheProjectsSchema() {
    String schemaPattern =
        ContractFiles.projectsSchemaTree()
            .at("/properties/projects/items/properties/id/pattern")
            .asString();

    assertThat(ProjectSettings.PROJECT_ID.pattern()).isEqualTo(schemaPattern);
    assertThat(ProjectSettings.MAX_ID_LENGTH)
        .isEqualTo(
            ContractFiles.projectsSchemaTree()
                .at("/properties/projects/items/properties/id/maxLength")
                .asInt());
  }

  @Test
  void idLongerThanTheLimitIsRejected() {
    assertThat(new ProjectSettings("a".repeat(40), "Name", null, null).id()).hasSize(40);
    assertThatThrownBy(() -> new ProjectSettings("a".repeat(41), "Name", null, null))
        .hasMessageContaining("at most 40 characters");
  }

  @Test
  void projectWithoutNameIsRejected() {
    assertThatThrownBy(() -> new ProjectSettings("rainmaker", " ", null, null))
        .hasMessageContaining("has no name");
  }

  @Test
  void settingsNeedAtLeastOneProject() {
    assertThatThrownBy(() -> new CollectorProperties(List.of(), Path.of("out"), null, Map.of()))
        .hasMessageContaining("must list a project");
  }

  @Test
  void projectIdsMustBeUnique() {
    assertThatThrownBy(
            () ->
                new CollectorProperties(
                    List.of(RAINMAKER, RAINMAKER), Path.of("out"), null, Map.of()))
        .hasMessageContaining("Two projects have the id 'rainmaker'");
  }

  @Test
  void legacySnapshotMustNameProject() {
    assertThatThrownBy(
            () -> new CollectorProperties(List.of(RAINMAKER), Path.of("out"), "missing", Map.of()))
        .hasMessageContaining("names no project: missing");
  }

  @Test
  void eachProjectHasItsOwnSnapshotFile() {
    CollectorProperties properties =
        new CollectorProperties(List.of(RAINMAKER, MISSION_CONTROL), Path.of("out"), "", Map.of());

    assertThat(properties.snapshotFile(MISSION_CONTROL))
        .isEqualTo(Path.of("out", "mission-control", "snapshot.json"));
    assertThat(properties.legacySnapshotProject()).isNull();
  }
}
