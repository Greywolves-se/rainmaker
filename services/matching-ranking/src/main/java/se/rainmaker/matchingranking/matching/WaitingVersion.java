package se.rainmaker.matchingranking.matching;

import java.util.List;
import java.util.UUID;
import se.rainmaker.contentintent.api.Topic;

/**
 * A green article version that couldn't be matched because G3 was down, with what the retry needs
 * to match it (#248).
 *
 * @param topics the version's topics, best first, as content-intent classified it
 * @param correlationId the correlation id of the event it came from, for the retry's log lines
 */
record WaitingVersion(UUID articleId, int version, List<Topic> topics, String correlationId) {}
