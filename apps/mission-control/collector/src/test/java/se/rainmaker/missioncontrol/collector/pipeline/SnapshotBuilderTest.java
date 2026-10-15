package se.rainmaker.missioncontrol.collector.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.rainmaker.missioncontrol.collector.snapshot.Project;
import se.rainmaker.missioncontrol.collector.snapshot.Risk;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.Sprint;

class SnapshotBuilderTest {

  private static final Instant NOW = Instant.parse("2026-10-15T06:00:00Z");
  private static final Project FALLBACK = new Project("Fallback", List.of());

  @Test
  void refusesTheSameIdTwiceInOneList() {
    SnapshotBuilder builder = new SnapshotBuilder().addRisk(risk("R-1"));

    assertThatThrownBy(() -> builder.addRisk(risk("R-1")))
        .isInstanceOf(SourceException.class)
        .hasMessage("risks: id R-1 is used twice");
  }

  @Test
  void reportsIdsAndSingleValuesAnotherContributionAlreadyHas() {
    SnapshotBuilder snapshot = new SnapshotBuilder().addRisk(risk("R-1")).sprint(sprint());
    SnapshotBuilder contribution =
        new SnapshotBuilder().addRisk(risk("R-1")).addRisk(risk("R-2")).sprint(sprint());

    assertThat(snapshot.conflictsWith(contribution))
        .containsExactly(
            "sprint is already set by another source",
            "risks: id R-1 is already used by another source");
  }

  @Test
  void addsContributionsInOrderAndUsesFallbackProject() {
    SnapshotBuilder snapshot = new SnapshotBuilder().addRisk(risk("R-2"));
    snapshot.add(new SnapshotBuilder().addRisk(risk("R-1")));

    Snapshot built = snapshot.build(NOW, FALLBACK, List.of());

    assertThat(built.risks()).extracting(Risk::id).containsExactly("R-2", "R-1");
    assertThat(built.project()).isEqualTo(FALLBACK);
    assertThat(built.generatedAt()).isEqualTo(NOW);
  }

  private static Risk risk(String id) {
    return new Risk(
        id, "Risk " + id, Risk.Level.RED, "Kim", "Plan B", "data-files", "https://x.org");
  }

  private static Sprint sprint() {
    return new Sprint(
        "Sprint 1",
        LocalDate.of(2026, 10, 12),
        LocalDate.of(2026, 10, 23),
        null,
        "github",
        "https://x.org");
  }
}
