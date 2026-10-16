package se.rainmaker.missioncontrol.collector.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties.SourceSettings;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotValidator;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import se.rainmaker.missioncontrol.collector.snapshot.WorkItem;

@ExtendWith(OutputCaptureExtension.class)
class CollectorPipelineTest {

  private static final Instant NOW = Instant.parse("2026-10-15T06:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final SnapshotValidator VALIDATOR = new SnapshotValidator();
  private static final ProjectSettings PROJECT =
      new ProjectSettings("example", "Example project", null, null);

  @Test
  void oneFailingSourceDoesNotStopTheOthers() {
    FakeSource github = FakeSource.withId("github").withWorkItem("1");
    FakeSource broken =
        FakeSource.withId("broken")
            .withWorkItem("2")
            .failingWith(new IllegalStateException("token ghp_secret rejected"));

    Snapshot snapshot = pipeline(Map.of(), github, broken).run(PROJECT);

    assertThat(snapshot.sources())
        .extracting(Source::id, Source::status)
        .containsExactly(tuple("broken", Source.Status.ERROR), tuple("github", Source.Status.OK));
    assertThat(snapshot.workItems()).extracting(WorkItem::id).containsExactly("1");
  }

  @Test
  void unexpectedErrorShowsNoExceptionMessage() {
    FakeSource broken =
        FakeSource.withId("broken").failingWith(new IllegalStateException("token ghp_secret"));

    Source result = pipeline(Map.of(), broken).run(PROJECT).sources().getFirst();

    assertThat(result.message()).doesNotContain("ghp_secret").contains("IllegalStateException");
    assertThat(result.fetchedAt()).isNull();
  }

  @Test
  void sourceExceptionMessageIsShown() {
    FakeSource broken =
        FakeSource.withId("broken").failingWith(new SourceException("GitHub answered 503"));

    Source result = pipeline(Map.of(), broken).run(PROJECT).sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).isEqualTo("GitHub answered 503");
  }

  @Test
  void successfulSourceIsOkWithFetchTime() {
    Snapshot snapshot = pipeline(Map.of(), FakeSource.withId("github")).run(PROJECT);

    assertThat(snapshot.generatedAt()).isEqualTo(NOW);
    assertThat(snapshot.sources().getFirst())
        .isEqualTo(new Source("github", "Fake github", Source.Status.OK, NOW, null));
  }

  @Test
  void disabledSourceIsListedButNotCalled() {
    FakeSource slack = FakeSource.withId("slack").withWorkItem("9");

    Snapshot snapshot = pipeline(Map.of("slack", new SourceSettings(false)), slack).run(PROJECT);

    assertThat(slack.calls()).isZero();
    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.DISABLED);
    assertThat(snapshot.workItems()).isEmpty();
  }

  @Test
  void sourcesAreAlwaysInIdOrder() {
    Snapshot snapshot =
        pipeline(Map.of(), FakeSource.withId("slack"), FakeSource.withId("data-files"))
            .run(PROJECT);

    assertThat(snapshot.sources()).extracting(Source::id).containsExactly("data-files", "slack");
  }

  @Test
  void sourceThatRepeatsAnIdIsAnErrorAndAddsNothing() {
    FakeSource first = FakeSource.withId("alpha").withWorkItem("7");
    FakeSource second = FakeSource.withId("beta").withWorkItem("8").withWorkItem("7");

    Snapshot snapshot = pipeline(Map.of(), first, second).run(PROJECT);

    assertThat(snapshot.workItems()).extracting(WorkItem::id).containsExactly("7");
    assertThat(snapshot.sources().get(1).status()).isEqualTo(Source.Status.ERROR);
    assertThat(snapshot.sources().get(1).message()).contains("workItems").contains("7");
  }

  @Test
  void usesTheConfiguredProjectNameWhenNoSourceSetsTheProject() {
    Snapshot snapshot = pipeline(Map.of()).run(PROJECT);

    assertThat(snapshot.project().name()).isEqualTo("Example project");
    assertThat(snapshot.project().milestones()).isEmpty();
    assertThat(snapshot.sprint()).isNull();
  }

  @Test
  void refusesTwoSourcesWithTheSameId() {
    assertThatThrownBy(() -> pipeline(Map.of(), FakeSource.withId("x"), FakeSource.withId("x")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("x");
  }

  @Test
  void refusesSourceIdTheContractDoesNotAllow() {
    assertThatThrownBy(() -> pipeline(Map.of(), FakeSource.withId("GitHub")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GitHub");
  }

  @Test
  void refusesSourceWithoutName() {
    SourceCollector nameless =
        new SourceCollector() {
          @Override
          public String id() {
            return "nameless";
          }

          @Override
          public String name() {
            return " ";
          }

          @Override
          public void collect(ProjectSettings project, SnapshotBuilder snapshot) {}
        };

    assertThatThrownBy(() -> pipeline(Map.of(), nameless))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nameless");
  }

  @Test
  void sourceWithDataThatBreaksTheContractIsAnErrorAndOthersAreKept() {
    FakeSource good = FakeSource.withId("alpha").withWorkItem("1");
    FakeSource bad = FakeSource.withId("beta").withWorkItem("2").withInvalidWorkItem("3");

    Snapshot snapshot = pipeline(Map.of(), good, bad).run(PROJECT);

    assertThat(snapshot.workItems()).extracting(WorkItem::id).containsExactly("1");
    Source result = snapshot.sources().get(1);
    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message())
        .contains("invalid data at")
        .contains("/workItems/1/url")
        .contains("format")
        .doesNotContain("ghp_secret");
  }

  @Test
  void sourceExceptionWithoutMessageStillGetsOne() {
    FakeSource broken = FakeSource.withId("broken").failingWith(new SourceException(" "));

    assertThat(pipeline(Map.of(), broken).run(PROJECT).sources().getFirst().message())
        .isEqualTo("Source failed");
  }

  @Test
  void warnsAboutSettingsForSourcesThatDoNotExist(CapturedOutput output) {
    pipeline(Map.of("githb", new SourceSettings(false)), FakeSource.withId("github"));

    assertThat(output.getOut())
        .contains("settings name sources that do not exist")
        .contains("githb");
  }

  @Test
  void sourceIdPatternIsTheSameAsInTheSchema() {
    String schemaPattern =
        ContractFiles.schemaTree().get("$defs").get("sourceId").get("pattern").asString();

    assertThat(CollectorPipeline.SOURCE_ID.pattern()).isEqualTo(schemaPattern);
  }

  private static CollectorPipeline pipeline(
      Map<String, SourceSettings> settings, SourceCollector... sources) {
    CollectorProperties properties =
        new CollectorProperties(List.of(PROJECT), Path.of("unused"), settings);
    return new CollectorPipeline(List.of(sources), properties, VALIDATOR, CLOCK);
  }
}
