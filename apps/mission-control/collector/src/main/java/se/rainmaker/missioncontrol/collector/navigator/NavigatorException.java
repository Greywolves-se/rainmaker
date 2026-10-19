package se.rainmaker.missioncontrol.collector.navigator;

/**
 * The AI text for a page could not be made. The message is safe to show on the dashboard: it never
 * quotes the API key, the request or OpenAI's answer.
 */
public class NavigatorException extends RuntimeException {

  private final boolean stopsRun;

  /** A failure of this page only; the other pages are still tried. */
  public NavigatorException(String message) {
    this(message, false);
  }

  private NavigatorException(String message, boolean stopsRun) {
    super(message);
    this.stopsRun = stopsRun;
  }

  /**
   * A failure every other call would hit too (a rejected key, a used-up quota, OpenAI not
   * answering), so the rest of the run is not tried.
   */
  public static NavigatorException forWholeRun(String message) {
    return new NavigatorException(message, true);
  }

  /** Whether the rest of the run should be skipped. */
  public boolean stopsRun() {
    return stopsRun;
  }
}
