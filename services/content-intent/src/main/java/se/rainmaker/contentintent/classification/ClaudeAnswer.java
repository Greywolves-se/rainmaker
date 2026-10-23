package se.rainmaker.contentintent.classification;

import java.util.List;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;

/**
 * A valid answer from Claude, already checked against the contract's schema.
 *
 * @param zone the zone Claude chose
 * @param topics one to three topics, without repeats, in Claude's order
 * @param reason the reason in Swedish and English
 */
record ClaudeAnswer(Zone zone, List<Topic> topics, Reason reason) {

  ClaudeAnswer {
    topics = List.copyOf(topics);
  }
}
