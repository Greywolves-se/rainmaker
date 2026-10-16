package se.rainmaker.missioncontrol.collector.snapshot;

import java.nio.file.Path;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.output.AtomicFiles;

/** Writes the snapshot files: each project's snapshot and {@code projects.json}. */
@Component
public class SnapshotWriter {

  /**
   * Writes {@code snapshot} to {@code output}, creating folders as needed. A reader never sees a
   * half-written snapshot, and a failed run leaves the previous one untouched ({@link
   * AtomicFiles}).
   */
  public void write(Snapshot snapshot, Path output) {
    AtomicFiles.write(output, file -> SnapshotJson.mapper().writeValue(file.toFile(), snapshot));
  }

  /** Writes {@code projects.json} the same way. */
  public void write(ProjectList projects, Path output) {
    AtomicFiles.write(output, file -> SnapshotJson.mapper().writeValue(file.toFile(), projects));
  }
}
