package se.rainmaker.missioncontrol.collector.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotValidatorTest {

  private final SnapshotValidator validator = new SnapshotValidator();

  @Test
  void sampleHasNoProblems() {
    assertThat(validator.problems(sample())).isEmpty();
  }

  @Test
  void problemNamesThePlaceAndTheRuleButNotTheValue() {
    Snapshot sample = sample();
    Risk risk = sample.risks().getFirst();
    Risk broken =
        new Risk(
            risk.id(),
            "",
            risk.level(),
            risk.owner(),
            risk.mitigation(),
            risk.source(),
            risk.url());
    Snapshot snapshot =
        new Snapshot(
            sample.schemaVersion(),
            sample.generatedAt(),
            sample.project(),
            sample.sources(),
            sample.sprint(),
            sample.workItems(),
            sample.pullRequests(),
            sample.builds(),
            sample.releases(),
            sample.decisions(),
            List.of(broken),
            sample.questions(),
            sample.links(),
            sample.changes(),
            sample.events(),
            sample.documents(),
            sample.navigator());

    assertThat(validator.problems(snapshot)).containsExactly("/risks/0/title (minLength)");
  }

  private static Snapshot sample() {
    return SnapshotJson.mapper().readValue(ContractFiles.sampleJson(), Snapshot.class);
  }
}
