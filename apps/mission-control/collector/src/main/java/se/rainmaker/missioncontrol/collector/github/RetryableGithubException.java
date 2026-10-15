package se.rainmaker.missioncontrol.collector.github;

/**
 * A failed GitHub request worth trying again: a server error, a timeout or a short rate limit. Only
 * used inside {@link GithubClient}; callers see a {@code SourceException}.
 */
final class RetryableGithubException extends RuntimeException {

  private final Long waitMillis;

  /**
   * Creates the exception.
   *
   * @param reason what went wrong, safe to show (e.g. "HTTP 503")
   * @param waitMillis how long GitHub asked us to wait, or null to use the normal back-off
   */
  RetryableGithubException(String reason, Long waitMillis) {
    super(reason);
    this.waitMillis = waitMillis;
  }

  /** How long GitHub asked us to wait, or null. */
  Long waitMillis() {
    return waitMillis;
  }
}
