package se.rainmaker.contentingestion.feed;

/** The feed could not be read as RSS. The next poll tries again. */
public class InvalidFeedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  InvalidFeedException(String message) {
    super(message);
  }

  InvalidFeedException(String message, Throwable cause) {
    super(message, cause);
  }
}
