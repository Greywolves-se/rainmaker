package se.rainmaker.publishercontrol.decision;

import java.util.UUID;

/** There is no suggestion with this id. */
public class SuggestionNotFoundException extends RuntimeException {

  SuggestionNotFoundException(UUID suggestionId) {
    super("No suggestion " + suggestionId);
  }
}
