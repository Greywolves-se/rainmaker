package se.rainmaker.missioncontrol.collector.navigator;

/**
 * The AI text for a page could not be made. The message is safe to show on the dashboard: it never
 * quotes the API key, the request or OpenAI's answer.
 */
public class NavigatorException extends RuntimeException {

  /** A failure with a message that is safe to show. */
  public NavigatorException(String message) {
    super(message);
  }
}
