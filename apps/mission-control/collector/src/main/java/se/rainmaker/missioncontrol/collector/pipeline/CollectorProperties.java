package se.rainmaker.missioncontrol.collector.pipeline;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Collector settings ({@code mission-control.collector.*} in {@code application.properties}).
 *
 * @param projects the projects to write a snapshot for, at least one, each with a unique id
 * @param outputFolder each project's files go to {@code <outputFolder>/<project id>/}; relative
 *     paths start at the working directory
 * @param legacySnapshotProject the project whose snapshot and START HERE report are also written as
 *     {@code <outputFolder>/snapshot.json} and {@code start-here.md}, where the web app reads them
 *     today; empty for none
 * @param sources per-source settings, keyed by source id; a source not listed is enabled
 */
@ConfigurationProperties("mission-control.collector")
public record CollectorProperties(
    List<ProjectSettings> projects,
    Path outputFolder,
    String legacySnapshotProject,
    Map<String, SourceSettings> sources) {

  /** Checks the required settings, so a missing one fails at startup with a clear message. */
  public CollectorProperties {
    if (projects == null || projects.isEmpty()) {
      throw new IllegalArgumentException("mission-control.collector.projects must list a project");
    }
    projects = List.copyOf(projects);
    Set<String> ids = new HashSet<>();
    for (ProjectSettings project : projects) {
      if (!ids.add(project.id())) {
        throw new IllegalArgumentException("Two projects have the id '" + project.id() + "'");
      }
    }
    Objects.requireNonNull(outputFolder, "mission-control.collector.output-folder must be set");
    legacySnapshotProject =
        legacySnapshotProject == null || legacySnapshotProject.isBlank()
            ? null
            : legacySnapshotProject;
    if (legacySnapshotProject != null && !ids.contains(legacySnapshotProject)) {
      throw new IllegalArgumentException(
          "mission-control.collector.legacy-snapshot-project names no project: "
              + legacySnapshotProject);
    }
    sources = sources == null ? Map.of() : Map.copyOf(sources);
  }

  /** Whether the source with this id should run. */
  public boolean isEnabled(String sourceId) {
    SourceSettings settings = sources.get(sourceId);
    return settings == null || settings.enabled();
  }

  /** Where the snapshot of {@code project} is written. */
  public Path snapshotFile(ProjectSettings project) {
    return outputFolder.resolve(project.id()).resolve("snapshot.json");
  }

  /**
   * Settings for one source.
   *
   * @param enabled false switches the source off; it is still listed as disabled
   */
  public record SourceSettings(@DefaultValue("true") boolean enabled) {}
}
