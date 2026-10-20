package se.rainmaker.missioncontrol.personnames;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PersonNamesTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static JsonNode json(String text) {
    return JSON.readTree(text);
  }

  @Test
  void collectsEveryPersonField() {
    JsonNode snapshot =
        json(
            """
            {"workItems": [{"assignees": ["Kim Ek", "kim-ek"]}],
             "pullRequests": [{"author": "gabbson42"}],
             "decisions": [{"owner": "Peter"}],
             "risks": [{"owner": "Sam Lind"}],
             "questions": [{"owner": "Alex Berg"}],
             "changes": [{"owner": "Maruf"}],
             "documents": [{"lastEditor": "Simon"}, {"lastEditor": null}]}
            """);

    assertThat(PersonNames.fromSnapshot(snapshot))
        .contains(
            "Kim Ek", "kim-ek", "gabbson42", "Peter", "Sam Lind", "Alex Berg", "Maruf", "Simon");
  }

  @Test
  void addsTheFirstNameOfFullName() {
    JsonNode snapshot = json("{\"risks\": [{\"owner\": \"Kim Ek\"}]}");

    assertThat(PersonNames.fromSnapshot(snapshot)).containsExactlyInAnyOrder("Kim Ek", "Kim");
  }

  @Test
  void splitsOwnerListsIntoEachName() {
    JsonNode snapshot =
        json(
            """
            {"decisions": [{"owner": "Mission Control developers (Emil, Gabriel, Mai)"}]}
            """);

    assertThat(PersonNames.fromSnapshot(snapshot))
        .containsExactlyInAnyOrder(
            "Mission Control developers (Emil, Gabriel, Mai)", "Emil", "Gabriel", "Mai");
  }

  @Test
  void neverAddsShortFirstNamesOrCommonWords() {
    JsonNode snapshot =
        json(
            """
            {"risks": [{"owner": "Al Berg"}, {"owner": "Team Rainmaker"},
                       {"owner": "kim ek"}]}
            """);

    assertThat(PersonNames.fromSnapshot(snapshot))
        .containsExactlyInAnyOrder("Al Berg", "Team Rainmaker", "kim ek");
  }

  @Test
  void neverAddsRoleWordsAsFirstNames() {
    // The role owners in data/rainmaker/questions.yaml.
    JsonNode snapshot =
        json(
            """
            {"questions": [{"owner": "Senior Dev"}, {"owner": "Senior Dev / Peter"},
                           {"owner": "Customer (Staffan)"}, {"owner": "Kund Ab"}]}
            """);

    assertThat(PersonNames.fromSnapshot(snapshot))
        .contains("Staffan", "Peter")
        .doesNotContain("Senior", "Customer", "Kund");
  }

  @Test
  void toleratesMissingFieldsAndNulls() {
    assertThat(PersonNames.fromSnapshot(json("{}"))).isEmpty();
    assertThat(
            PersonNames.fromSnapshot(
                json("{\"workItems\": [{\"assignees\": null}], \"risks\": [{\"owner\": \"\"}]}")))
        .isEmpty();
    assertThat(PersonNames.fromSnapshot(null)).isEmpty();
  }

  @Test
  void ownerListGivesEachNameButNotTheGroupLabel() {
    assertThat(PersonNames.ownerNames("Mission Control developers (Emil, Gabriel, Mai)"))
        .containsExactlyInAnyOrder(
            "Mission Control developers (Emil, Gabriel, Mai)", "Emil", "Gabriel", "Mai");
  }

  @Test
  void ordinaryWordsInOwnerAreNotTakenForNames() {
    assertThat(PersonNames.ownerNames("Kalle och teamet"))
        .containsExactlyInAnyOrder("Kalle och teamet", "Kalle");
    assertThat(PersonNames.ownerNames("Peter/Staffan"))
        .containsExactlyInAnyOrder("Peter/Staffan", "Peter", "Staffan");
  }

  @Test
  void singleOwnerIsTakenWhole() {
    assertThat(PersonNames.ownerNames("Kim Ek")).containsExactly("Kim Ek");
  }

  @Test
  void findsEveryNameInTheSharedContractSample() throws IOException {
    Path sample = Path.of("..", "contract", "fixtures", "snapshot.sample.json");

    assertThat(PersonNames.fromSnapshot(JSON.readTree(Files.readString(sample))))
        .contains("Kim Ek", "Kim", "Sam Lind", "Sam", "Alex Berg", "Alex");
  }
}
