package se.rainmaker.missioncontrol.collector.pipeline;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Collector settings ({@code mission-control.collector.*} in {@code application.properties}).
 *
 * @param projectName project name used when no source provides the project
 * @param output where the snapshot is written; relative paths start at the working directory
 * @param sources per-source settings, keyed by source id; a source not listed is enabled
 */
@ConfigurationProperties("mission-control.collector")
public record CollectorProperties(
    String projectName, Path output, Map<String, SourceSettings> sources) {

  /** Checks the required settings, so a missing one fails at startup with a clear message. */
  public CollectorProperties {
    if (projectName == null || projectName.isBlank()) {
      throw new IllegalArgumentException("mission-control.collector.project-name must be set");
    }
    Objects.requireNonNull(output, "mission-control.collector.output must be set");
    sources = sources == null ? Map.of() : Map.copyOf(sources);
  }

  /** Whether the source with this id should run. */
  public boolean isEnabled(String sourceId) {
    SourceSettings settings = sources.get(sourceId);
    return settings == null || settings.enabled();
  }

  /**
   * Settings for one source.
   *
   * @param enabled false switches the source off; it is still listed as disabled
   */
  public record SourceSettings(@DefaultValue("true") boolean enabled) {}
}
