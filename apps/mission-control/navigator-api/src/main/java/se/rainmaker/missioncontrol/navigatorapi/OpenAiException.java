package se.rainmaker.missioncontrol.navigatorapi;

/**
 * OpenAI could not answer. The message is short and safe to log: it never contains the key, the
 * question or the body of OpenAI's error response.
 */
public final class OpenAiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** How the API answers the reader: {@code 503} or {@code 504} (ADR 0003). */
  public enum Kind {
    UNAVAILABLE,
    TIMEOUT
  }

  private final Kind kind;

  OpenAiException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  /** Whether OpenAI was unavailable or too slow. */
  public Kind kind() {
    return kind;
  }
}
