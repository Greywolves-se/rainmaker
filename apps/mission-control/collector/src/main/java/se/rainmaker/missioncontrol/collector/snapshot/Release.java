package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;

/** The version running in one environment. */
public record Release(
    String id,
    String version,
    Environment environment,
    Instant publishedAt,
    String source,
    String url) {

  /** Deployment environment. */
  public enum Environment {
    DEV,
    UAT,
    PROD
  }
}
