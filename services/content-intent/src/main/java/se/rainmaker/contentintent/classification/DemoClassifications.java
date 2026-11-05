package se.rainmaker.contentintent.classification;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Known answers for the fake classifier, one per headline (#261): the test site's articles
 * classified as Claude would, so a local demo without Bedrock shows green, yellow and red articles
 * with their own topics. Read once at start-up; a broken file stops G2.
 *
 * <pre>{"articles": [{"headline": "…", "zone": "green", "topics": ["travel", "food"],
 *                    "reason": {"sv": "…", "en": "…"}}]}</pre>
 */
final class DemoClassifications {

  /** No demo answers: every article gets the fake's configured classification. */
  static final DemoClassifications NONE = new DemoClassifications(Map.of());

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Map<String, Answer> byHeadline;

  private DemoClassifications(Map<String, Answer> byHeadline) {
    this.byHeadline = Map.copyOf(byHeadline);
  }

  /**
   * Reads and checks a demo file.
   *
   * @param source where the file came from, for the error message
   * @throws IllegalArgumentException if the file is not valid, naming the source and the problem
   */
  static DemoClassifications read(InputStream json, String source) {
    JsonNode articles;
    try {
      articles = JSON.readTree(json).path("articles");
    } catch (JacksonException notJson) {
      throw new IllegalArgumentException(source + ": not valid JSON", notJson);
    }
    if (!articles.isArray() || articles.isEmpty()) {
      throw new IllegalArgumentException(source + ": needs a non-empty list \"articles\"");
    }
    Map<String, Answer> byHeadline = new HashMap<>();
    for (JsonNode article : articles) {
      String headline = article.path("headline").stringValue("").strip();
      try {
        Answer answer = answer(article);
        if (headline.isEmpty()) {
          throw new IllegalArgumentException("an article has no headline");
        }
        if (byHeadline.putIfAbsent(headline, answer) != null) {
          throw new IllegalArgumentException("the headline is listed twice");
        }
      } catch (IllegalArgumentException problem) {
        throw new IllegalArgumentException(
            source + ": " + problem.getMessage() + " (\"" + headline + "\")", problem);
      }
    }
    return new DemoClassifications(byHeadline);
  }

  /** The answer for this headline, if the file lists it; surrounding spaces are ignored. */
  Optional<Answer> find(String headline) {
    return Optional.ofNullable(byHeadline.get(headline.strip()));
  }

  /** How many headlines the file lists. */
  int size() {
    return byHeadline.size();
  }

  private static Answer answer(JsonNode article) {
    String zoneKey = article.path("zone").stringValue("");
    Zone zone =
        Zone.fromKey(zoneKey)
            .orElseThrow(() -> new IllegalArgumentException("unknown zone \"" + zoneKey + "\""));
    List<Topic> topics = new ArrayList<>();
    for (JsonNode topic : article.path("topics")) {
      String key = topic.stringValue("");
      topics.add(
          Topic.fromKey(key)
              .orElseThrow(() -> new IllegalArgumentException("unknown topic \"" + key + "\"")));
    }
    if (topics.isEmpty() || topics.size() > Classification.MAX_TOPICS) {
      throw new IllegalArgumentException("needs 1-" + Classification.MAX_TOPICS + " topics");
    }
    JsonNode reason = article.path("reason");
    return new Answer(
        zone,
        topics,
        new Reason(reason.path("sv").stringValue(null), reason.path("en").stringValue(null)));
  }

  /** The classification the fake gives a listed headline. */
  record Answer(Zone zone, List<Topic> topics, Reason reason) {

    /** Makes the topics unmodifiable. */
    Answer {
      topics = List.copyOf(topics);
    }
  }
}
