package se.rainmaker.publishercontrol.articles;

/** G2's articles API could not deliver a usable answer, even after the retries. */
public class ArticlesUnavailableException extends RuntimeException {

  ArticlesUnavailableException(String message) {
    super(message);
  }

  ArticlesUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
