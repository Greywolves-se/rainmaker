package se.rainmaker.missioncontrol.collector.datafiles;

import java.nio.file.Path;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the data-file source ({@code mission-control.collector.data-files.*}).
 *
 * @param folder folder with the YAML files; relative paths start at the working directory
 * @param filesUrl GitHub address of that folder, used to link every item to its line
 */
@ConfigurationProperties("mission-control.collector.data-files")
public record DataFilesProperties(Path folder, String filesUrl) {

  /** Fails at startup if a setting is missing. */
  public DataFilesProperties {
    Objects.requireNonNull(folder, "mission-control.collector.data-files.folder must be set");
    Objects.requireNonNull(filesUrl, "mission-control.collector.data-files.files-url must be set");
  }
}
