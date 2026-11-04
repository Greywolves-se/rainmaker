package se.rainmaker.contentingestion.site;

/** The publisher's site did not answer, or answered with an error. The next poll tries again. */
public class SiteUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  SiteUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
