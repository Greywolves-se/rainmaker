package se.rainmaker.contentintent.classification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converts between Jackson's JSON tree and the AWS SDK's {@link Document}, which Bedrock uses for
 * the tool's input schema and for the tool input Claude returns.
 */
final class JsonDocuments {

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private JsonDocuments() {}

  /** The JSON tree as a Document, e.g. the answer schema sent to Bedrock. */
  static Document toDocument(JsonNode node) {
    if (node.isObject()) {
      Map<String, Document> map = new LinkedHashMap<>();
      node.properties().forEach(entry -> map.put(entry.getKey(), toDocument(entry.getValue())));
      return Document.fromMap(map);
    }
    if (node.isArray()) {
      List<Document> list = new ArrayList<>();
      node.values().forEach(item -> list.add(toDocument(item)));
      return Document.fromList(list);
    }
    if (node.isString()) {
      return Document.fromString(node.asString());
    }
    if (node.isNumber()) {
      return Document.fromNumber(node.decimalValue());
    }
    if (node.isBoolean()) {
      return Document.fromBoolean(node.asBoolean());
    }
    return Document.fromNull();
  }

  /** The Document as a JSON tree, e.g. the tool input Claude returned. */
  static JsonNode toJson(Document document) {
    if (document == null || document.isNull()) {
      return NODES.nullNode();
    }
    if (document.isMap()) {
      ObjectNode object = NODES.objectNode();
      document.asMap().forEach((key, value) -> object.set(key, toJson(value)));
      return object;
    }
    if (document.isList()) {
      ArrayNode array = NODES.arrayNode();
      document.asList().forEach(item -> array.add(toJson(item)));
      return array;
    }
    if (document.isString()) {
      return NODES.stringNode(document.asString());
    }
    if (document.isNumber()) {
      return NODES.numberNode(document.asNumber().bigDecimalValue());
    }
    return NODES.booleanNode(document.asBoolean());
  }
}
