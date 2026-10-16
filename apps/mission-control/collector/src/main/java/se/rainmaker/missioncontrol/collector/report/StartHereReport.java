package se.rainmaker.missioncontrol.collector.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.rainmaker.missioncontrol.collector.datafiles.DataFilesProperties;
import se.rainmaker.missioncontrol.collector.output.AtomicFiles;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;

/**
 * Writes {@code start-here.md} next to the snapshot (#25). The hand-written intro (product goal,
 * architecture in short) is {@code start-here-intro.md} in the data folder; the rest comes from the
 * snapshot ({@link StartHereMarkdown}).
 */
@Component
public class StartHereReport {

  static final String FILE_NAME = "start-here.md";
  static final String INTRO_FILE = "start-here-intro.md";
  static final String MISSING_INTRO =
      "_No intro yet: add `apps/mission-control/data/" + INTRO_FILE + "`._";

  private static final Logger log = LoggerFactory.getLogger(StartHereReport.class);

  private final DataFilesProperties dataFiles;

  /** Reads the intro from the data folder in the settings. */
  public StartHereReport(DataFilesProperties dataFiles) {
    this.dataFiles = dataFiles;
  }

  /** Writes the report into the same folder as {@code snapshotFile}; returns its path. */
  public Path write(Snapshot snapshot, Path snapshotFile) {
    Path output = snapshotFile.resolveSibling(FILE_NAME);
    String markdown = StartHereMarkdown.render(snapshot, intro());
    AtomicFiles.write(output, file -> Files.writeString(file, markdown));
    return output;
  }

  private String intro() {
    Path file = dataFiles.folder().resolve(INTRO_FILE);
    if (!Files.exists(file)) {
      log.atInfo().addKeyValue("file", INTRO_FILE).log("START HERE intro not found");
      return MISSING_INTRO;
    }
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    }
  }
}
