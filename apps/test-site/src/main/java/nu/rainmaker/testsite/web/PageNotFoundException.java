package nu.rainmaker.testsite.web;

/** Thrown when an article or section does not exist; shown as the 404 page. */
public class PageNotFoundException extends RuntimeException {

  /** Creates the exception. */
  public PageNotFoundException(String what) {
    super("Not found: " + what);
  }
}
