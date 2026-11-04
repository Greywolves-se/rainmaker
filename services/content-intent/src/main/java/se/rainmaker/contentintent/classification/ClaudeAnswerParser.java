package se.rainmaker.contentintent.classification;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads Claude's answer and accepts it only if it matches the contract's schema exactly (#145,
 * section 8): {@code content-intent/classification-answer.schema.json} on the classpath.
 *
 * <p>The schema check covers the limits that Bedrock's structured output may not enforce (1-3
 * topics, at most 300 characters per reason). The code adds what JSON Schema cannot say: a reason
 * must not be blank, and repeated topics are kept once. Anything else, including an answer wrapped
 * in Markdown, is rejected, so the caller stores the classification as failed.
 */
final class ClaudeAnswerParser {

  static final String SCHEMA_RESOURCE = "content-intent/classification-answer.schema.json";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final JsonNode schemaJson;
  private final Schema schema;

  /** Loads the schema; fails at start-up if it is missing. */
  ClaudeAnswerParser() {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(SCHEMA_RESOURCE + " is not on the classpath");
      }
      schemaJson = JSON.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + SCHEMA_RESOURCE, e);
    }
    schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(schemaJson.toString(), InputFormat.JSON);
  }

  /**
   * The schema to give Bedrock as the tool's input schema: the same file, without the {@code
   * $schema} and {@code $comment} keys, which only matter here.
   */
  JsonNode toolInputSchema() {
    ObjectNode copy = (ObjectNode) schemaJson.deepCopy();
    copy.remove("$schema");
    copy.remove("$comment");
    return copy;
  }

  /** The answer if it is valid JSON text, otherwise empty. Never throws for a bad answer. */
  Optional<ClaudeAnswer> parse(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    try {
      return parse(JSON.readTree(text));
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  /** The answer if the JSON tree is valid, otherwise empty. Never throws for a bad answer. */
  Optional<ClaudeAnswer> parse(JsonNode answer) {
    if (answer == null || !schema.validate(answer).isEmpty()) {
      return Optional.empty();
    }
    String sv = answer.get("reason").get("sv").asString();
    String en = answer.get("reason").get("en").asString();
    if (sv.isBlank() || en.isBlank()) {
      return Optional.empty();
    }
    // The schema allows only known keys, so fromKey always finds the value here.
    Set<Topic> topics = new LinkedHashSet<>();
    answer
        .get("topics")
        .forEach(topic -> topics.add(Topic.fromKey(topic.asString()).orElseThrow()));
    Zone zone = Zone.fromKey(answer.get("zone").asString()).orElseThrow();
    return Optional.of(new ClaudeAnswer(zone, List.copyOf(topics), new Reason(sv, en)));
  }
}
