package se.rainmaker.missioncontrol.collector.drive;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Drive settings ({@code mission-control.collector.google-drive.*}, #113).
 *
 * @param apiUrl Drive REST API address, e.g. {@code https://www.googleapis.com/drive/v3}
 * @param folders the Drive folder id per project id; a project not listed has no Drive documents
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for an answer
 * @param maxAttempts attempts per request, including the first
 * @param backoff wait before the first retry; it doubles for each retry
 */
@ConfigurationProperties("mission-control.collector.google-drive")
public record GoogleDriveProperties(
    URI apiUrl,
    Map<String, String> folders,
    Duration connectTimeout,
    Duration readTimeout,
    int maxAttempts,
    Duration backoff) {

  /**
   * Drive ids are letters, digits, dashes and underscores. Checked because the id goes into a Drive
   * search query: a quote in it could change what the query finds.
   */
  static final Pattern FOLDER_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

  /** Checks the settings at startup. */
  public GoogleDriveProperties {
    Objects.requireNonNull(apiUrl, "mission-control.collector.google-drive.api-url must be set");
    folders = folders == null ? Map.of() : Map.copyOf(folders);
    folders.forEach(
        (project, folder) -> {
          if (folder == null || !FOLDER_ID.matcher(folder).matches()) {
            throw new IllegalArgumentException(
                "Google Drive folder id for project '" + project + "' is not a valid Drive id");
          }
        });
    Objects.requireNonNull(connectTimeout, "connect-timeout must be set");
    Objects.requireNonNull(readTimeout, "read-timeout must be set");
    Objects.requireNonNull(backoff, "backoff must be set");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("max-attempts must be at least 1");
    }
  }
}
