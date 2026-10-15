package se.rainmaker.missioncontrol.collector.snapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

/** Writes the snapshot file. */
@Component
public class SnapshotWriter {

  /**
   * Writes {@code snapshot} to {@code output}, creating folders as needed. It writes to a temporary
   * file in the same folder and then moves it into place in one step, so a reader never sees a
   * half-written snapshot and a failed run leaves the previous snapshot untouched.
   */
  public void write(Snapshot snapshot, Path output) {
    Path folder = output.toAbsolutePath().getParent();
    try {
      Files.createDirectories(folder);
      Path temporary = Files.createTempFile(folder, output.getFileName().toString(), ".tmp");
      try {
        SnapshotJson.mapper().writeValue(temporary.toFile(), snapshot);
        Files.move(
            temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write the snapshot to " + output, e);
    }
  }
}
