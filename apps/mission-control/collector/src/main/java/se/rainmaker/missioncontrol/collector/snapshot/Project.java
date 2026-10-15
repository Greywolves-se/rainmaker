package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.LocalDate;
import java.util.List;

/** The project the snapshot describes, with its key dates. */
public record Project(String name, List<Project.Milestone> milestones) {

  /** Copies the list, so the project cannot change after it is built. */
  public Project {
    milestones = List.copyOf(milestones);
  }

  /** A key date, e.g. Goal 1 or pilot start. */
  public record Milestone(String id, String title, LocalDate date) {}
}
