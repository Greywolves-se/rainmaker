package se.rainmaker.missioncontrol.collector.snapshot;

import java.util.List;

/**
 * {@code projects.json}: the projects that have a snapshot (contract {@code projects.schema.json},
 * #99).
 */
public record ProjectList(int schemaVersion, List<Entry> projects) {

  /** The contract version this record implements. */
  public static final int SCHEMA_VERSION = 1;

  /** Copies the list, so it cannot change after it is built. */
  public ProjectList {
    projects = List.copyOf(projects);
  }

  /** One project: its id (also its folder) and display name. */
  public record Entry(String id, String name) {}
}
