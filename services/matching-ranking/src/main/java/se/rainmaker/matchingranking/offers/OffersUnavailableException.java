package se.rainmaker.matchingranking.offers;

/** G3's offers couldn't be read: G3 is down, too slow, or answered something unusable. */
public class OffersUnavailableException extends RuntimeException {

  /** Creates the exception with the reason and the underlying error. */
  public OffersUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Creates the exception with the reason. */
  public OffersUnavailableException(String message) {
    super(message);
  }
}
