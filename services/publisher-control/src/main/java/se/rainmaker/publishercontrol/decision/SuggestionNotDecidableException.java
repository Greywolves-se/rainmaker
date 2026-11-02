package se.rainmaker.publishercontrol.decision;

import java.util.UUID;

/**
 * The suggestion can't be approved or rejected: it was withdrawn, belongs to an earlier article
 * version, or its article isn't classified + green (contract 3.3 and 5).
 */
public class SuggestionNotDecidableException extends RuntimeException {

  SuggestionNotDecidableException(UUID suggestionId) {
    super("Suggestion " + suggestionId + " can no longer be approved or rejected");
  }
}
