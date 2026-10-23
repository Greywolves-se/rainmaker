package se.rainmaker.contentintent.api;

import java.time.Instant;
import java.util.List;

/**
 * The result of classifying one article version (#145, section 3.2).
 *
 * <p>Only {@link ClassificationStatus#CLASSIFIED} has a zone, topics and a reason. Failed and
 * unclassified articles are treated exactly like red everywhere (decision D-4): no suggestions, no
 * links, no panel.
 *
 * @param status where the classification stands
 * @param zone the zone, or null unless classified
 * @param topics one to three topics if classified, otherwise empty
 * @param reason why, in Swedish and English, or null unless classified
 * @param model the model id exactly as sent to Bedrock, or null if no call was made
 * @param classifiedAt when the answer (or the failure) came, or null if unclassified
 */
public record Classification(
    ClassificationStatus status,
    Zone zone,
    List<Topic> topics,
    Reason reason,
    String model,
    Instant classifiedAt) {

  /** Most topics Claude may pick for one article. */
  public static final int MAX_TOPICS = 3;

  /** Checks that the fields match the status. */
  public Classification {
    topics = List.copyOf(topics);
    boolean classified = status == ClassificationStatus.CLASSIFIED;
    if (classified
        && (zone == null
            || reason == null
            || topics.isEmpty()
            || topics.size() > MAX_TOPICS
            || model == null
            || model.isBlank()
            || classifiedAt == null)) {
      throw new IllegalArgumentException(
          "A classified article needs a zone, 1-3 topics, a reason, the model and a time");
    }
    if (!classified && (zone != null || reason != null || !topics.isEmpty())) {
      throw new IllegalArgumentException("Only a classified article has a zone, topics or reason");
    }
  }

  /** A valid answer from the model. */
  public static Classification classified(
      Zone zone, List<Topic> topics, Reason reason, String model, Instant classifiedAt) {
    return new Classification(
        ClassificationStatus.CLASSIFIED, zone, topics, reason, model, classifiedAt);
  }

  /** The call or the answer failed; treated like red. */
  public static Classification failed(String model, Instant at) {
    return new Classification(ClassificationStatus.FAILED, null, List.of(), null, model, at);
  }

  /** Not classified yet, or deferred by the call limits; treated like red. */
  public static Classification unclassified() {
    return new Classification(ClassificationStatus.UNCLASSIFIED, null, List.of(), null, null, null);
  }

  /** True only for a classified green article (decision D-4); never by default. */
  public boolean mayGetSuggestions() {
    return status == ClassificationStatus.CLASSIFIED && zone == Zone.GREEN;
  }
}
