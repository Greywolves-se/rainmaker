package se.rainmaker.contentingestion.page;

/** An article page without the parts ingestion needs. The next poll tries again. */
public class InvalidArticlePageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  InvalidArticlePageException(String message) {
    super(message);
  }
}
