package se.rainmaker.missioncontrol.collector.pipeline;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One project the collector writes a snapshot for ({@code mission-control.collector.projects[n].*},
 * #99). GitHub issues and pull requests belong to a project by their labels; labels are compared
 * without case.
 *
 * @param id lowercase letters, digits and dashes; names the project's output folder
 * @param name display name, used when the data files do not name the project
 * @param withLabel only GitHub items with this label belong to the project; empty means no rule
 * @param withoutLabel GitHub items with this label do not belong to the project; empty means no
 *     rule
 */
public record ProjectSettings(String id, String name, String withLabel, String withoutLabel) {

  /** Same pattern as source ids; the id becomes a folder name, so it can never hold "/" or "..". */
  static final Pattern PROJECT_ID = Pattern.compile("^[a-z][a-z0-9-]*$");

  /** Checks the settings at startup. */
  public ProjectSettings {
    if (id == null || !PROJECT_ID.matcher(id).matches()) {
      throw new IllegalArgumentException(
          "Project id '" + id + "' must match " + PROJECT_ID.pattern());
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Project '" + id + "' has no name");
    }
    withLabel = normalized(withLabel);
    withoutLabel = normalized(withoutLabel);
  }

  /** Whether a GitHub item with these labels belongs to this project. */
  public boolean includes(List<String> labels) {
    List<String> lower = labels.stream().map(label -> label.toLowerCase(Locale.ROOT)).toList();
    return (withLabel == null || lower.contains(withLabel))
        && (withoutLabel == null || !lower.contains(withoutLabel));
  }

  private static String normalized(String label) {
    return label == null || label.isBlank() ? null : label.strip().toLowerCase(Locale.ROOT);
  }
}
