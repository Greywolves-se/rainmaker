package nu.rainmaker.testsite.content;

/**
 * Thrown when a content file is missing a field or has a wrong value. The message always names the
 * file and the field, so the problem is easy to find.
 */
public class ContentValidationException extends RuntimeException {

  /** Creates the exception for a problem in {@code source} (usually a file name). */
  public ContentValidationException(String source, String problem) {
    super(source + ": " + problem);
  }

  /** Creates the exception with the underlying cause, e.g. a JSON syntax error. */
  public ContentValidationException(String source, String problem, Throwable cause) {
    super(source + ": " + problem, cause);
  }
}
