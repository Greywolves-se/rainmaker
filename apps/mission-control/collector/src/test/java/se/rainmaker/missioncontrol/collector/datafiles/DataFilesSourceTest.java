package se.rainmaker.missioncontrol.collector.datafiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorPipeline;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;
import se.rainmaker.missioncontrol.collector.snapshot.Decision;
import se.rainmaker.missioncontrol.collector.snapshot.Link;
import se.rainmaker.missioncontrol.collector.snapshot.Question;
import se.rainmaker.missioncontrol.collector.snapshot.Risk;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.Source;

class DataFilesSourceTest {

  private static final String FILES_URL = "https://github.com/example-org/repo/blob/develop/data";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-15T06:00:00Z"), ZoneOffset.UTC);

  @TempDir Path folder;

  @Test
  void readsEveryFileIntoTheSnapshot() throws IOException {
    write(
        "project.yaml",
        """
        name: Rainmaker
        milestones:
          - id: goal-1
            title: Technically ready in UAT
            date: 2026-12-18
        """);
    write(
        "decisions.yaml",
        """
        # Decision log
        - id: B1
          title: Use a monorepo
          owner: Simon
          date: 2026-10-02
          status: decided
          reference: Section 5
        - id: B34
          title: Mission Control V1 snapshot
          owner: Simon
          date: 2026-10-15
          status: proposed
          needsOwnerDecision: true
        """);
    write(
        "risks.yaml",
        """
        - id: R-1
          title: Pilot publisher not signed
          level: red
          owner: Peter
          mitigation: Two backup publishers
        """);
    write(
        "questions.yaml",
        """
        - id: Q-1
          title: Which consent text?
          owner: Peter
          neededBy: 2026-10-30
          status: open
          critical: true
        - id: Q-2
          title: UAT copy of the decision log?
          owner: Simon
          status: answered
        """);
    write(
        "links.yaml",
        """
        - id: repo
          title: Repository
          category: document
          url: https://github.com/example-org/repo
        """);
    write(
        "changelog.yaml",
        """
        - id: C-1
          date: 2026-10-14
          title: ECR repositories created
          owner: Maruf
          rollbackPlan: Delete the repositories
        """);

    Snapshot snapshot = run();

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.project().name()).isEqualTo("Rainmaker");
    assertThat(snapshot.project().milestones().getFirst().date())
        .isEqualTo(LocalDate.of(2026, 12, 18));
    assertThat(snapshot.decisions())
        .extracting(Decision::id, Decision::needsOwnerDecision, Decision::reference, Decision::url)
        .containsExactly(
            tuple("B1", false, "Section 5", FILES_URL + "/decisions.yaml#L2"),
            tuple("B34", true, null, FILES_URL + "/decisions.yaml#L8"));
    assertThat(snapshot.risks()).extracting(Risk::level).containsExactly(Risk.Level.RED);
    assertThat(snapshot.questions())
        .extracting(Question::neededBy, Question::critical)
        .containsExactly(tuple(LocalDate.of(2026, 10, 30), true), tuple(null, false));
    assertThat(snapshot.links())
        .extracting(Link::url)
        .containsExactly("https://github.com/example-org/repo");
    assertThat(snapshot.changes()).hasSize(1);
  }

  @Test
  void missingFilesMeanNoItems() {
    Snapshot snapshot = run();

    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.OK);
    assertThat(snapshot.decisions()).isEmpty();
    assertThat(snapshot.project().name()).isEqualTo("Fallback project");
  }

  @ParameterizedTest(name = "{2}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          risks.yaml     | '- id: R-1\\n  title: T\\n  level: red\\n  mitigation: M'                                          | risks.yaml:1 owner: is required
          risks.yaml     | '- id: R-1\\n  title: T\\n  level: purple\\n  owner: O\\n  mitigation: M'                        | risks.yaml:1 level: must be one of green, yellow, red
          risks.yaml     | '- id: R-1\\n  title: T\\n  level: red\\n  owner: O\\n  mitigation: M\\n  ownr: X'                | risks.yaml:1 ownr: unknown field
          risks.yaml     | '- id: R-1\\n  title: T\\n  level: red\\n  owner: O\\n  mitigation: M\\n- id: R-1\\n  title: T\\n  level: red\\n  owner: O\\n  mitigation: M' | risks.yaml:6 id: R-1 is used twice
          changelog.yaml | '- id: C-1\\n  date: 14 Oct\\n  title: T\\n  owner: O\\n  rollbackPlan: R'                           | changelog.yaml:1 date: must be a date like 2026-12-18
          links.yaml     | '- id: x\\n  title: T\\n  category: document\\n  url: not a link'                                | links.yaml:1 url: must be an http(s) link
          questions.yaml | '- id: Q\\n  title: T\\n  owner: O\\n  status: open\\n  critical: maybe'                          | questions.yaml:1 critical: must be true or false
          decisions.yaml | 'id: B1'                                                                                       | decisions.yaml: must be a list of items
          decisions.yaml | '- id: B1\\n  title: [unclosed'                                                                 | decisions.yaml:2: is not valid YAML
          risks.yaml     | '[]\\n- id: R-1\\n  title: T\\n  level: red\\n  owner: O\\n  mitigation: M'                 | risks.yaml:2: unexpected content; if you added items, remove the '[]' line
          """)
  void invalidFileMakesTheSourceAnErrorAndAddsNothing(String file, String content, String message)
      throws IOException {
    write("project.yaml", "name: From file\n");
    write(file, content.replace("\\n", "\n"));

    Snapshot snapshot = run();

    Source result = snapshot.sources().getFirst();
    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).contains(message);
    assertThat(snapshot.project().name()).isEqualTo("Fallback project");
  }

  @Test
  void theRealDataFilesAreValid() {
    Path realData = Path.of("..", "data");

    Snapshot snapshot = run(realData);

    assertThat(snapshot.sources().getFirst())
        .as("apps/mission-control/data must be valid; see the message")
        .extracting(Source::status, Source::message)
        .containsExactly(Source.Status.OK, null);
  }

  private Snapshot run() {
    return run(folder);
  }

  private static Snapshot run(Path dataFolder) {
    DataFilesSource source = new DataFilesSource(new DataFilesProperties(dataFolder, FILES_URL));
    CollectorProperties properties =
        new CollectorProperties("Fallback project", Path.of("unused.json"), Map.of());
    return new CollectorPipeline(List.of(source), properties, new SnapshotValidator(), CLOCK).run();
  }

  private void write(String file, String content) throws IOException {
    Files.writeString(folder.resolve(file), content);
  }
}
