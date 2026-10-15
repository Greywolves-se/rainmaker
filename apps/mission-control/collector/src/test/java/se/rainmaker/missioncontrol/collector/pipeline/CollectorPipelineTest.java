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
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties.SourceSettings;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import se.rainmaker.missioncontrol.collector.snapshot.WorkItem;

class CollectorPipelineTest {

  private static final Instant NOW = Instant.parse("2026-10-15T06:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void oneFailingSourceDoesNotStopTheOthers() {
    FakeSource github = FakeSource.withId("github").withWorkItem("1");
    FakeSource broken =
        FakeSource.withId("broken")
            .withWorkItem("2")
            .failingWith(new IllegalStateException("token ghp_secret rejected"));

    Snapshot snapshot = pipeline(Map.of(), github, broken).run();

    assertThat(snapshot.sources())
        .extracting(Source::id, Source::status)
        .containsExactly(tuple("broken", Source.Status.ERROR), tuple("github", Source.Status.OK));
    assertThat(snapshot.workItems()).extracting(WorkItem::id).containsExactly("1");
  }

  @Test
  void unexpectedErrorShowsNoExceptionMessage() {
    FakeSource broken =
        FakeSource.withId("broken").failingWith(new IllegalStateException("token ghp_secret"));

    Source result = pipeline(Map.of(), broken).run().sources().getFirst();

    assertThat(result.message()).doesNotContain("ghp_secret").contains("IllegalStateException");
    assertThat(result.fetchedAt()).isNull();
  }

  @Test
  void sourceExceptionMessageIsShown() {
    FakeSource broken =
        FakeSource.withId("broken").failingWith(new SourceException("GitHub answered 503"));

    Source result = pipeline(Map.of(), broken).run().sources().getFirst();

    assertThat(result.status()).isEqualTo(Source.Status.ERROR);
    assertThat(result.message()).isEqualTo("GitHub answered 503");
  }

  @Test
  void successfulSourceIsOkWithFetchTime() {
    Snapshot snapshot = pipeline(Map.of(), FakeSource.withId("github")).run();

    assertThat(snapshot.generatedAt()).isEqualTo(NOW);
    assertThat(snapshot.sources().getFirst())
        .isEqualTo(new Source("github", "Fake github", Source.Status.OK, NOW, null));
  }

  @Test
  void disabledSourceIsListedButNotCalled() {
    FakeSource slack = FakeSource.withId("slack").withWorkItem("9");

    Snapshot snapshot = pipeline(Map.of("slack", new SourceSettings(false)), slack).run();

    assertThat(slack.calls()).isZero();
    assertThat(snapshot.sources().getFirst().status()).isEqualTo(Source.Status.DISABLED);
    assertThat(snapshot.workItems()).isEmpty();
  }

  @Test
  void sourcesAreAlwaysInIdOrder() {
    Snapshot snapshot =
        pipeline(Map.of(), FakeSource.withId("slack"), FakeSource.withId("data-files")).run();

    assertThat(snapshot.sources()).extracting(Source::id).containsExactly("data-files", "slack");
  }

  @Test
  void sourceThatRepeatsAnIdIsAnErrorAndAddsNothing() {
    FakeSource first = FakeSource.withId("alpha").withWorkItem("7");
    FakeSource second = FakeSource.withId("beta").withWorkItem("8").withWorkItem("7");

    Snapshot snapshot = pipeline(Map.of(), first, second).run();

    assertThat(snapshot.workItems()).extracting(WorkItem::id).containsExactly("7");
    assertThat(snapshot.sources().get(1).status()).isEqualTo(Source.Status.ERROR);
    assertThat(snapshot.sources().get(1).message()).contains("workItems").contains("7");
  }

  @Test
  void usesTheConfiguredProjectNameWhenNoSourceSetsTheProject() {
    Snapshot snapshot = pipeline(Map.of()).run();

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

  private static CollectorPipeline pipeline(
      Map<String, SourceSettings> settings, SourceCollector... sources) {
    CollectorProperties properties =
        new CollectorProperties("Example project", Path.of("unused.json"), settings);
    return new CollectorPipeline(List.of(sources), properties, CLOCK);
  }
}
