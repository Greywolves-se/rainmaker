package se.rainmaker.missioncontrol.navigatorapi;

import java.util.Optional;

/**
 * The data one page shows, and the names to hide (ADR 0003). In production it is read from the
 * project's snapshot in the private S3 bucket, never from the request.
 */
@FunctionalInterface
public interface PageDataSource {

  /** Returns the page's data, or empty when the snapshot cannot be read. */
  Optional<PageData> pageData(String project, String page);
}
