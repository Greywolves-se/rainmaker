package se.rainmaker.missioncontrol.collector.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import se.rainmaker.missioncontrol.collector.snapshot.ContractFiles;
import se.rainmaker.missioncontrol.collector.snapshot.Decision;
import se.rainmaker.missioncontrol.collector.snapshot.Link;
import se.rainmaker.missioncontrol.collector.snapshot.Project;
import se.rainmaker.missioncontrol.collector.snapshot.PullRequest;
import se.rainmaker.missioncontrol.collector.snapshot.Question;
import se.rainmaker.missioncontrol.collector.snapshot.Risk;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;
import se.rainmaker.missioncontrol.collector.snapshot.Source;
import se.rainmaker.missioncontrol.collector.snapshot.WorkItem;

class StartHereMarkdownTest {

  private static final String INTRO = "## What we build\nA test intro.";
  private static final Path GOLDEN = Path.of("src/test/resources/start-here/sample.md");

  /**
   * The report for the shared sample snapshot must equal the reviewed golden file. When it does
   * not, the new output is written to target/start-here-actual.md, so it can be diffed and, after
   * review, copied over the golden file.
   */
  @Test
  void sampleSnapshotMatchesTheGoldenFile() throws IOException {
    String actual = StartHereMarkdown.render(sample(), INTRO);

    if (!Files.exists(GOLDEN) || !Files.readString(GOLDEN).equals(actual)) {
      Files.createDirectories(Path.of("target"));
      Files.writeString(Path.of("target/start-here-actual.md"), actual);
    }
    assertThat(GOLDEN).exists();
    assertThat(actual).isEqualTo(Files.readString(GOLDEN));
  }

  @Test
  void idsAreSortedByTheirNumberNotAsText() {
    List<String> ids = new ArrayList<>(List.of("B22", "B3", "B33", "B4", "C15", "C2"));

    ids.sort(StartHereMarkdown.BY_ID);

    assertThat(ids).containsExactly("B3", "B4", "B22", "B33", "C2", "C15");
  }

  @Test
  void latestDecisionsOnTheSameDayShowTheNewestIds() {
    Snapshot sample = sample();
    LocalDate day = LocalDate.of(2026, 10, 4);
    String url = "https://example.org/decisions.yaml#L1";
    List<Decision> decisions = new ArrayList<>();
    for (int i = 22; i <= 33; i++) {
      decisions.add(
          new Decision(
              "B" + i,
              "Decision " + i,
              "Simon",
              day,
              Decision.Status.DECIDED,
              false,
              null,
              "data-files",
              url));
    }

    String report =
        StartHereMarkdown.render(withLists(sample, sample.workItems(), decisions), INTRO);

    assertThat(report).contains("[B33]").contains("[B29]").doesNotContain("[B28]");
  }

  @Test
  void sameDataInAnotherOrderGivesTheSameFile() {
    Snapshot sample = sample();
    Snapshot shuffled = withLists(sample, shuffle(sample.workItems()), shuffle(sample.decisions()));

    assertThat(StartHereMarkdown.render(shuffled, INTRO))
        .isEqualTo(StartHereMarkdown.render(sample, INTRO));
  }

  @Test
  void staysUnderTwoPagesForLargeProject() {
    String report = StartHereMarkdown.render(large(), INTRO);

    assertThat(report.lines().count()).isLessThanOrEqualTo(120);
    assertThat(report).contains("and 95 more");
  }

  @Test
  void titlesCannotAddLinksOrHtml() {
    Snapshot sample = sample();
    WorkItem item = sample.workItems().getFirst();
    WorkItem tricky =
        new WorkItem(
            item.id(),
            "Fix [this](javascript:alert(1)) <b>now</b> *please*",
            item.status(),
            item.assignees(),
            item.labels(),
            item.inCurrentSprint(),
            item.createdAt(),
            item.closedAt(),
            item.source(),
            item.url());

    String report =
        StartHereMarkdown.render(withLists(sample, List.of(tricky), sample.decisions()), INTRO);

    assertThat(report)
        .contains("Fix \\[this\\](javascript:alert(1)) \\<b\\>now\\</b\\> \\*please\\*")
        .doesNotContain("[this](javascript");
  }

  @Test
  void onlyWebAddressesBecomeLinks() {
    assertThat(StartHereMarkdown.link("Docs", "https://example.org/a b(1)"))
        .isEqualTo("[Docs](https://example.org/a%20b(1%29)");
    assertThat(StartHereMarkdown.link("Docs", "HTTP://example.org"))
        .isEqualTo("[Docs](HTTP://example.org)");
    assertThat(StartHereMarkdown.link("Run", "javascript:alert(1)")).isEqualTo("Run");
    assertThat(StartHereMarkdown.link("Run", "data:text/html,hi")).isEqualTo("Run");
    assertThat(StartHereMarkdown.link("File", "ftp://example.org/file")).isEqualTo("File");
    assertThat(StartHereMarkdown.link("Page", "/relative/page")).isEqualTo("Page");
    assertThat(StartHereMarkdown.link("Broken", "https://exa mple.org:x")).isEqualTo("Broken");
    assertThat(StartHereMarkdown.link("Empty", "")).isEqualTo("Empty");
    assertThat(StartHereMarkdown.link("None", null)).isEqualTo("None");
  }

  @Test
  void lineBreaksOfEveryKindBecomeSpaces() {
    assertThat(StartHereMarkdown.escape("one\rtwo\r\nthree\nfour")).isEqualTo("one two three four");
  }

  @Test
  void milestoneDaysAreCountedFromTheSnapshotDate() {
    LocalDate today = LocalDate.of(2026, 10, 16);
    Project project =
        new Project(
            "Rainmaker",
            List.of(
                new Project.Milestone("m1", "Today", today),
                new Project.Milestone("m2", "Tomorrow", today.plusDays(1)),
                new Project.Milestone("m3", "Soon", today.plusDays(3)),
                new Project.Milestone("m4", "Yesterday", today.minusDays(1)),
                new Project.Milestone("m5", "Earlier", today.minusDays(3))));

    String report =
        StartHereMarkdown.render(
            snapshot(project, List.of(), List.of(), "2026-10-16T23:30:00Z"), "");

    assertThat(report)
        .contains("**Today:** 2026-10-16 (today)")
        .contains("**Tomorrow:** 2026-10-17 (1 day left)")
        .contains("**Soon:** 2026-10-19 (3 days left)")
        .contains("**Yesterday:** 2026-10-15 (1 day ago)")
        .contains("**Earlier:** 2026-10-13 (3 days ago)");
  }

  @Test
  void withoutSprintNextWorkListsAllOpenWork() {
    List<WorkItem> items =
        List.of(
            workItem("1", "Planned", true, WorkItem.Status.TODO),
            workItem("2", "Not planned", false, WorkItem.Status.IN_PROGRESS),
            workItem("3", "Finished", false, WorkItem.Status.DONE));

    String report = StartHereMarkdown.render(snapshot(emptyProject(), items, List.of()), "");

    assertThat(report)
        .contains("Open work, blocked and in progress first.")
        .contains("**In progress** [Not planned]")
        .contains("**To do** [Planned]")
        .doesNotContain("Finished");
  }

  @Test
  void questionsWithoutNeededByComeLast() {
    String url = "https://example.org/questions.yaml#L1";
    List<Question> questions =
        List.of(
            new Question("C1", "Undated", "Peter", null, Question.Status.OPEN, false, "data", url),
            new Question(
                "C2",
                "Dated",
                "Peter",
                LocalDate.of(2026, 12, 1),
                Question.Status.OPEN,
                false,
                "data",
                url));

    String report = StartHereMarkdown.render(snapshot(emptyProject(), List.of(), questions), "");

    assertThat(report.indexOf("[C2]")).isPositive().isLessThan(report.indexOf("[C1]"));
    assertThat(report).contains("[C1](" + url + ") Undated – Peter\n");
  }

  @Test
  void emptyProjectGivesShortReportWithEmptyStates() {
    Snapshot empty =
        new Snapshot(
            Snapshot.SCHEMA_VERSION,
            Instant.parse("2026-10-16T08:00:00Z"),
            new Project("Rainmaker", List.of()),
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String report = StartHereMarkdown.render(empty, "");

    assertThat(report)
        .contains("# START HERE – Rainmaker")
        .contains("No sprint is running.")
        .contains("Nothing open.")
        .contains("No red or yellow risks.")
        .contains("No releases yet.");
  }

  private static Snapshot sample() {
    return SnapshotJson.mapper().readValue(ContractFiles.sampleJson(), Snapshot.class);
  }

  private static Project emptyProject() {
    return new Project("Rainmaker", List.of());
  }

  private static Snapshot snapshot(
      Project project, List<WorkItem> items, List<Question> questions) {
    return snapshot(project, items, questions, "2026-10-16T08:00:00Z");
  }

  private static Snapshot snapshot(
      Project project, List<WorkItem> items, List<Question> questions, String generatedAt) {
    return new Snapshot(
        Snapshot.SCHEMA_VERSION,
        Instant.parse(generatedAt),
        project,
        List.of(),
        null,
        items,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        questions,
        List.of(),
        List.of(),
        List.of());
  }

  private static WorkItem workItem(
      String id, String title, boolean inCurrentSprint, WorkItem.Status status) {
    return new WorkItem(
        id,
        title,
        status,
        List.of(),
        List.of(),
        inCurrentSprint,
        Instant.parse("2026-10-01T08:00:00Z"),
        null,
        "github",
        "https://github.com/example-org/example-repo/issues/" + id);
  }

  private static <T> List<T> shuffle(List<T> items) {
    List<T> copy = new ArrayList<>(items);
    Collections.reverse(copy);
    Collections.shuffle(copy, new Random(42));
    return copy;
  }

  private static Snapshot withLists(Snapshot s, List<WorkItem> items, List<Decision> decisions) {
    return new Snapshot(
        s.schemaVersion(),
        s.generatedAt(),
        s.project(),
        s.sources(),
        s.sprint(),
        items,
        s.pullRequests(),
        s.builds(),
        s.releases(),
        decisions,
        s.risks(),
        s.questions(),
        s.links(),
        s.changes(),
        s.events());
  }

  /** The sample with 100 of everything a report lists. */
  private static Snapshot large() {
    Snapshot s = sample();
    Instant created = Instant.parse("2026-10-01T08:00:00Z");
    LocalDate day = LocalDate.of(2026, 10, 1);
    String url = "https://github.com/example-org/example-repo/issues/1";
    List<WorkItem> items = new ArrayList<>();
    List<PullRequest> pulls = new ArrayList<>();
    List<Decision> decisions = new ArrayList<>();
    List<Risk> risks = new ArrayList<>();
    List<Question> questions = new ArrayList<>();
    List<Link> links = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      items.add(
          new WorkItem(
              "w" + i,
              "Item " + i,
              WorkItem.Status.TODO,
              List.of(),
              List.of(),
              true,
              created,
              null,
              "github",
              url));
      pulls.add(
          new PullRequest(
              "p" + i,
              i,
              "PR " + i,
              "mai",
              false,
              PullRequest.State.OPEN,
              PullRequest.Review.PENDING,
              PullRequest.Checks.PASSING,
              created,
              null,
              "github",
              url));
      decisions.add(
          new Decision(
              "B" + i,
              "Decision " + i,
              "Simon",
              day,
              Decision.Status.PROPOSED,
              true,
              null,
              "data-files",
              url));
      risks.add(new Risk("R" + i, "Risk " + i, Risk.Level.RED, "Peter", "Plan", "data-files", url));
      questions.add(
          new Question(
              "Q" + i,
              "Question " + i,
              "Peter",
              day,
              Question.Status.OPEN,
              true,
              "data-files",
              url));
      links.add(new Link("l" + i, "Link " + i, Link.Category.DOCUMENT, "data-files", url));
    }
    List<Source> sources = List.of(new Source("github", "GitHub", Source.Status.OK, created, null));
    return new Snapshot(
        s.schemaVersion(),
        s.generatedAt(),
        s.project(),
        sources,
        s.sprint(),
        items,
        pulls,
        s.builds(),
        s.releases(),
        decisions,
        risks,
        questions,
        links,
        s.changes(),
        s.events());
  }
}
