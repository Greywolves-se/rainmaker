package se.rainmaker.missioncontrol.collector.pipeline;

/**
 * A source could not deliver its data. The message is shown on the dashboard as the source's status
 * message, so it must be short, useful and free of secrets.
 */
public class SourceException extends RuntimeException {

  /** Creates the exception with a message that is safe to show. */
  public SourceException(String message) {
    super(message);
  }

  /** Creates the exception with a message that is safe to show and the underlying cause. */
  public SourceException(String message, Throwable cause) {
    super(message, cause);
  }
}
