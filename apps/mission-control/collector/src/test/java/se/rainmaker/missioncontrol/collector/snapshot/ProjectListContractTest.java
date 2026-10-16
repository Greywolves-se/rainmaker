package se.rainmaker.missioncontrol.collector.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.Schema;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Checks {@code projects.schema.json}, its sample and the {@link ProjectList} record (#99). */
class ProjectListContractTest {

  private static final Schema SCHEMA = ContractFiles.projectsSchema();

  @Test
  void sampleMatchesSchema() {
    assertThat(SCHEMA.validate(ContractFiles.projectsSampleTree())).isEmpty();
  }

  @Test
  void recordReadsAndWritesTheSampleUnchanged() {
    ProjectList projects =
        SnapshotJson.mapper().readValue(ContractFiles.projectsSampleJson(), ProjectList.class);

    assertThat(projects.projects())
        .extracting(ProjectList.Entry::id)
        .containsExactly("rainmaker", "mission-control");
    JsonNode written = SnapshotJson.mapper().valueToTree(projects);
    assertThat(written).isEqualTo(ContractFiles.projectsSampleTree());
  }

  @Test
  void projectListBuiltInCodeMatchesSchema() {
    ProjectList projects =
        new ProjectList(
            ProjectList.SCHEMA_VERSION, List.of(new ProjectList.Entry("rainmaker", "Rainmaker")));

    assertThat(SCHEMA.validate(SnapshotJson.mapper().valueToTree(projects))).isEmpty();
  }

  @Test
  void rejectsEmptyList() {
    ObjectNode projects = ContractFiles.projectsSampleTree();
    ((ArrayNode) projects.get("projects")).removeAll();

    assertThat(SCHEMA.validate(projects)).isNotEmpty();
  }

  @Test
  void rejectsIdThatIsNoFolderName() {
    ObjectNode projects = ContractFiles.projectsSampleTree();
    ((ObjectNode) projects.get("projects").get(0)).put("id", "../Rainmaker");

    assertThat(SCHEMA.validate(projects)).isNotEmpty();
  }

  @Test
  void rejectsUnknownAndMissingFields() {
    ObjectNode unknown = ContractFiles.projectsSampleTree();
    ((ObjectNode) unknown.get("projects").get(0)).put("colour", "blue");
    ObjectNode missing = ContractFiles.projectsSampleTree();
    ((ObjectNode) missing.get("projects").get(0)).remove("name");

    assertThat(SCHEMA.validate(unknown)).isNotEmpty();
    assertThat(SCHEMA.validate(missing)).isNotEmpty();
  }

  @Test
  void recordRejectsUnknownField() {
    ObjectNode projects = ContractFiles.projectsSampleTree();
    projects.put("costSek", 1200);

    assertThatThrownBy(
            () -> SnapshotJson.mapper().readValue(projects.toString(), ProjectList.class))
        .hasMessageContaining("costSek");
  }
}
