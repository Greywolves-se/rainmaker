package se.rainmaker.missioncontrol.collector.navigator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageDataTest {

  @Test
  void ownerListGivesEachNameButNotTheGroupLabel() {
    assertThat(PageData.ownerNames("Mission Control developers (Emil, Gabriel, Mai)"))
        .containsExactlyInAnyOrder(
            "Mission Control developers (Emil, Gabriel, Mai)", "Emil", "Gabriel", "Mai");
  }

  @Test
  void ordinaryWordsInOwnerAreNotTakenForNames() {
    assertThat(PageData.ownerNames("Kalle och teamet"))
        .containsExactlyInAnyOrder("Kalle och teamet", "Kalle");
    assertThat(PageData.ownerNames("Peter/Staffan"))
        .containsExactlyInAnyOrder("Peter/Staffan", "Peter", "Staffan");
  }

  @Test
  void singleOwnerIsTakenWhole() {
    assertThat(PageData.ownerNames("Kim Ek")).containsExactly("Kim Ek");
  }
}
