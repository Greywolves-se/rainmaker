package se.rainmaker.missioncontrol.collector.datafiles;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.pipeline.ProjectSettings;
import se.rainmaker.missioncontrol.collector.pipeline.SnapshotBuilder;
import se.rainmaker.missioncontrol.collector.pipeline.SourceCollector;
import se.rainmaker.missioncontrol.collector.pipeline.SourceException;
import se.rainmaker.missioncontrol.collector.snapshot.Change;
import se.rainmaker.missioncontrol.collector.snapshot.Decision;
import se.rainmaker.missioncontrol.collector.snapshot.Link;
import se.rainmaker.missioncontrol.collector.snapshot.Project;
import se.rainmaker.missioncontrol.collector.snapshot.Question;
import se.rainmaker.missioncontrol.collector.snapshot.Risk;

/**
 * The hand-maintained YAML files in {@code apps/mission-control/data/<project id>/}: project,
 * decisions, risks, questions, links and the change log. If any file has a problem, the whole
 * source reports an error listing the problems (file, line, field) and adds nothing.
 */
@Component
public class DataFilesSource implements SourceCollector {

  static final String ID = "data-files";
  private static final int MAX_PROBLEMS_SHOWN = 5;
  private static final Logger log = LoggerFactory.getLogger(DataFilesSource.class);

  private final DataFilesProperties properties;

  /** Reads from the folder in the settings. */
  public DataFilesSource(DataFilesProperties properties) {
    this.properties = properties;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String name() {
    return "Data files";
  }

  @Override
  public void collect(ProjectSettings project, SnapshotBuilder snapshot) {
    List<String> problems = new ArrayList<>();
    DataFileReader files =
        new DataFileReader(
            properties.folder().resolve(project.id()),
            properties.filesUrl() + "/" + project.id(),
            problems);

    final Project projectFile =
        files.object("project.yaml").map(DataFilesSource::project).orElse(null);
    final List<Decision> decisions = read(files, "decisions.yaml", DataFilesSource::decision);
    final List<Risk> risks = read(files, "risks.yaml", DataFilesSource::risk);
    final List<Question> questions = read(files, "questions.yaml", DataFilesSource::question);
    final List<Link> links = read(files, "links.yaml", DataFilesSource::link);
    final List<Change> changes = read(files, "changelog.yaml", DataFilesSource::change);

    if (!problems.isEmpty()) {
      // The dashboard message is cut to a few problems, so the full list is logged here.
      log.atWarn()
          .addKeyValue("problemCount", problems.size())
          .addKeyValue("problems", problems)
          .log("data files have problems");
      throw new SourceException(summary(problems));
    }
    if (projectFile != null) {
      snapshot.project(projectFile);
    }
    decisions.forEach(snapshot::addDecision);
    risks.forEach(snapshot::addRisk);
    questions.forEach(snapshot::addQuestion);
    links.forEach(snapshot::addLink);
    changes.forEach(snapshot::addChange);
  }

  private static <T> List<T> read(DataFileReader files, String file, Function<DataItem, T> toItem) {
    return files.list(file).stream().map(toItem).toList();
  }

  private static Project project(DataItem item) {
    List<Project.Milestone> milestones =
        item.items("milestones").stream()
            .map(
                milestone -> {
                  Project.Milestone result =
                      new Project.Milestone(
                          milestone.text("id"), milestone.text("title"), milestone.date("date"));
                  milestone.checkNoOtherFields();
                  return result;
                })
            .toList();
    Project project = new Project(item.text("name"), milestones);
    item.checkNoOtherFields();
    return project;
  }

  private static Decision decision(DataItem item) {
    Decision decision =
        new Decision(
            item.text("id"),
            item.text("title"),
            item.text("owner"),
            item.date("date"),
            item.choice("status", Decision.Status.class),
            item.flag("needsOwnerDecision"),
            item.optionalText("reference"),
            ID,
            item.url());
    item.checkNoOtherFields();
    return decision;
  }

  private static Risk risk(DataItem item) {
    Risk risk =
        new Risk(
            item.text("id"),
            item.text("title"),
            item.choice("level", Risk.Level.class),
            item.text("owner"),
            item.text("mitigation"),
            ID,
            item.url());
    item.checkNoOtherFields();
    return risk;
  }

  private static Question question(DataItem item) {
    Question question =
        new Question(
            item.text("id"),
            item.text("title"),
            item.text("owner"),
            item.optionalDate("neededBy"),
            item.choice("status", Question.Status.class),
            item.flag("critical"),
            ID,
            item.url());
    item.checkNoOtherFields();
    return question;
  }

  private static Link link(DataItem item) {
    Link link =
        new Link(
            item.text("id"),
            item.text("title"),
            item.choice("category", Link.Category.class),
            ID,
            item.link("url"));
    item.checkNoOtherFields();
    return link;
  }

  private static Change change(DataItem item) {
    Change change =
        new Change(
            item.text("id"),
            item.date("date"),
            item.text("title"),
            item.text("owner"),
            item.text("rollbackPlan"),
            ID,
            item.url());
    item.checkNoOtherFields();
    return change;
  }

  private static String summary(List<String> problems) {
    String shown = String.join("; ", problems.stream().limit(MAX_PROBLEMS_SHOWN).toList());
    int more = problems.size() - MAX_PROBLEMS_SHOWN;
    return more > 0 ? shown + " (and " + more + " more)" : shown;
  }
}
