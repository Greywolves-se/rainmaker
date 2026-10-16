package se.rainmaker.missioncontrol.collector.snapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes output files (the snapshot, the reports) so that a reader never sees half a file: first to
 * a temporary file in the same folder, then moved into place in one step. A failed write leaves the
 * previous file untouched.
 */
public final class AtomicFiles {

  /**
   * Owner can write, everyone can read: the web container may run as another user than the
   * collector (shared volume in docker-compose, staging).
   */
  static final Set<PosixFilePermission> READABLE_BY_ALL =
      PosixFilePermissions.fromString("rw-r--r--");

  private static final Logger log = LoggerFactory.getLogger(AtomicFiles.class);

  /** Writes the content of one file to the temporary path it is given. */
  @FunctionalInterface
  public interface Content {
    /** Writes the whole file to {@code file}. */
    void writeTo(Path file) throws IOException;
  }

  private AtomicFiles() {}

  /** Writes {@code output} with {@code content}, creating folders as needed. */
  public static void write(Path output, Content content) {
    Path folder = output.toAbsolutePath().getParent();
    try {
      Files.createDirectories(folder);
      Path temporary = Files.createTempFile(folder, output.getFileName().toString(), ".tmp");
      try {
        content.writeTo(temporary);
        makeReadableByAll(temporary);
        moveIntoPlace(temporary, output);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + output, e);
    }
  }

  /** Temporary files are created owner-only (rw-------); output files must be readable by all. */
  private static void makeReadableByAll(Path file) throws IOException {
    if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(file, READABLE_BY_ALL);
    }
  }

  private static void moveIntoPlace(Path temporary, Path output) throws IOException {
    try {
      Files.move(
          temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      // Some bind mounts (e.g. Docker Desktop on macOS) cannot move atomically. A plain replace
      // is still far better than failing the run; a reader may then briefly see no file.
      log.atWarn()
          .addKeyValue("output", output.toString())
          .log("atomic move not supported here; replacing the file non-atomically");
      Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
