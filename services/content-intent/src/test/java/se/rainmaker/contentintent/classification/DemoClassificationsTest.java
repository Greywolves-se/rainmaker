package se.rainmaker.contentintent.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;

/** The fake's demo classifications: one known answer per test-site headline (#261). */
class DemoClassificationsTest {

  private static final String DEMO_FILE = "content-intent/demo-classifications.json";

  private static final String MALLORCA =
      """
      {"headline": "Svenskarna väljer Mallorca i höst", "zone": "green",
       "topics": ["travel", "food"], "reason": {"sv": "Resor.", "en": "Travel."}}
      """;

  @Test
  void givesTheListedHeadlineItsZoneTopicsAndReason() {
    DemoClassifications demo = articles(MALLORCA);

    assertThat(demo.find("Svenskarna väljer Mallorca i höst"))
        .contains(
            new DemoClassifications.Answer(
                Zone.GREEN, List.of(Topic.TRAVEL, Topic.FOOD), new Reason("Resor.", "Travel.")));
  }

  /** The classifier only sees the headline and the text, so the headline is the key. */
  @Test
  void matchesOnlyTheExactHeadline() {
    DemoClassifications demo = articles(MALLORCA);

    assertThat(demo.find("SVENSKARNA VÄLJER MALLORCA I HÖST")).isEmpty();
    assertThat(demo.find("Svenskarna väljer Mallorca")).isEmpty();
    assertThat(demo.find("  Svenskarna väljer Mallorca i höst ")).isPresent();
  }

  @Test
  void hasNothingForAnUnlistedHeadline() {
    assertThat(articles(MALLORCA).find("Två döda i trafikolycka")).isEmpty();
    assertThat(DemoClassifications.NONE.find("Svenskarna väljer Mallorca i höst")).isEmpty();
  }

  /** Not JSON, or no list of articles. */
  @ParameterizedTest
  @ValueSource(strings = {"not json", "{}", "{\"articles\": []}", "{\"articles\": {}}"})
  void refusesFileWithoutArticles(String json) {
    assertThatThrownBy(() -> json(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("test.json: ");
  }

  /** Each entry needs a headline, a known zone, 1-3 known topics and both reasons. */
  @ParameterizedTest
  @MethodSource("brokenEntries")
  void refusesBrokenEntry(String entry) {
    assertThatThrownBy(() -> articles(entry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("test.json: ");
  }

  static Stream<String> brokenEntries() {
    return Stream.of(
        entryWith("headline", null),
        entryWith("zone", "\"purple\""),
        entryWith("topics", "[\"golf\"]"),
        entryWith("topics", "[]"),
        entryWith("topics", "[\"travel\", \"food\", \"home\", \"cars\"]"),
        entryWith("reason", "{\"sv\": \"\", \"en\": \"E.\"}"),
        entryWith("reason", null));
  }

  @Test
  void refusesTheSameHeadlineTwice() {
    assertThatThrownBy(() -> articles(MALLORCA, MALLORCA))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Svenskarna väljer Mallorca i höst");
  }

  /** The demo's file follows the contract's zone rules (3.3) and the product rules. */
  @Test
  void demoFileKeepsCrimeAndAccidentsRedAndHealthAndMoneyYellow() throws IOException {
    DemoClassifications demo;
    try (InputStream file = getClass().getClassLoader().getResourceAsStream(DEMO_FILE)) {
      demo = DemoClassifications.read(file, DEMO_FILE);
    }

    assertThat(demo.size()).isEqualTo(12);
    Map<String, Zone> sensitive =
        Map.of(
            "Man gripen efter rånförsök mot butik i centrala Vintra", Zone.RED,
            "Två döda i trafikolycka utanför Vintra", Zone.RED,
            "Allt fler unga sover för lite – läkaren: ”Skärmarna är bara en del av problemet”",
                Zone.YELLOW,
            "Hushållen sparar mer än någonsin – men var fjärde saknar buffert", Zone.YELLOW);
    sensitive.forEach(
        (headline, zone) ->
            assertThat(demo.find(headline))
                .as(headline)
                .hasValueSatisfying(answer -> assertThat(answer.zone()).isEqualTo(zone)));
    assertThat(demo.find("Svenskarna väljer Mallorca i höst – här är öns okända pärlor"))
        .hasValueSatisfying(
            answer -> {
              assertThat(answer.zone()).isEqualTo(Zone.GREEN);
              assertThat(answer.topics()).containsExactly(Topic.TRAVEL, Topic.FOOD);
            });
  }

  /** A valid entry with one field's JSON replaced, or left out when {@code json} is null. */
  private static String entryWith(String field, String json) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("headline", "\"H\"");
    fields.put("zone", "\"green\"");
    fields.put("topics", "[\"travel\"]");
    fields.put("reason", "{\"sv\": \"S.\", \"en\": \"E.\"}");
    if (json == null) {
      fields.remove(field);
    } else {
      fields.put(field, json);
    }
    return fields.entrySet().stream()
        .map(each -> "\"" + each.getKey() + "\": " + each.getValue())
        .collect(Collectors.joining(", ", "{", "}"));
  }

  /** A file with these entries in its list of articles. */
  private static DemoClassifications articles(String... entries) {
    return json("{\"articles\": [" + String.join(", ", entries) + "]}");
  }

  private static DemoClassifications json(String json) {
    return DemoClassifications.read(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.json");
  }
}
