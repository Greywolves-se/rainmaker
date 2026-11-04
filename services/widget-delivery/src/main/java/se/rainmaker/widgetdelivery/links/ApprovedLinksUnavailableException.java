package se.rainmaker.widgetdelivery.links;

/**
 * G3 couldn't say which links an article may have. Brand safety fails closed: no links, never the
 * last known answer (contract 7).
 */
class ApprovedLinksUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  ApprovedLinksUnavailableException(String message) {
    super(message);
  }

  ApprovedLinksUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
