package se.rainmaker.contentintent.classification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.rainmaker.contentintent.api.ArticleToClassify;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.ClassificationStatus;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;

/** The fake classifier lets G2 run and be tested without Bedrock (#148, Getting Started 5.3). */
class FakeArticleClassifierTest {

  private static final Instant NOW = Instant.parse("2026-10-30T09:15:04Z");
  private static final Reason REASON = new Reason("Låtsasklassificering.", "Fake classification.");
  private static final String MARKER = "[e2e-green]";

  @Test
  void answersEveryArticleWithTheConfiguredClassification() {
    FakeArticleClassifier classifier = classifier(Zone.GREEN, "");

    Classification classification = classifier.classify(article("Rubrik"));

    assertThat(classification.status()).isEqualTo(ClassificationStatus.CLASSIFIED);
    assertThat(classification.zone()).isEqualTo(Zone.GREEN);
    assertThat(classification.topics()).containsExactly(Topic.HOME);
    assertThat(classification.reason()).isEqualTo(REASON);
    assertThat(classification.model()).isEqualTo(FakeArticleClassifier.MODEL);
    assertThat(classification.classifiedAt()).isEqualTo(NOW);
  }

  /** The end-to-end test needs a green and a red article in one run (#154). */
  @Test
  void headlineWithTheGreenMarkerIsGreenAndOthersKeepTheConfiguredZone() {
    FakeArticleClassifier classifier = classifier(Zone.RED, MARKER);

    assertThat(classifier.classify(article("Nytt badrum " + MARKER)).zone()).isEqualTo(Zone.GREEN);
    assertThat(classifier.classify(article("Två döda i trafikolycka")).zone()).isEqualTo(Zone.RED);
  }

  /** Fails closed: without a marker, nothing turns green, whatever the headline says. */
  @Test
  void withoutMarkerNoHeadlineTurnsGreen() {
    FakeArticleClassifier classifier = classifier(Zone.RED, "");

    assertThat(classifier.classify(article("Nytt badrum " + MARKER)).zone()).isEqualTo(Zone.RED);
    assertThat(classifier.classify(article("Nytt badrum")).zone()).isEqualTo(Zone.RED);
  }

  /** The demo file's answer for a listed headline; every other article is as before (#261). */
  @Test
  void listedHeadlineGetsItsDemoClassificationAndOthersAreAsBefore() {
    DemoClassifications demo =
        DemoClassifications.read(
            new ByteArrayInputStream(
                """
                {"articles": [{"headline": "Mallorca i höst", "zone": "green",
                  "topics": ["travel", "food"], "reason": {"sv": "Resor.", "en": "Travel."}}]}
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "test.json");
    FakeArticleClassifier classifier = classifier(Zone.RED, MARKER, demo);

    Classification listed = classifier.classify(article("Mallorca i höst"));
    assertThat(listed.zone()).isEqualTo(Zone.GREEN);
    assertThat(listed.topics()).containsExactly(Topic.TRAVEL, Topic.FOOD);
    assertThat(listed.reason()).isEqualTo(new Reason("Resor.", "Travel."));
    assertThat(listed.model()).isEqualTo(FakeArticleClassifier.MODEL);
    assertThat(listed.classifiedAt()).isEqualTo(NOW);

    Classification unlisted = classifier.classify(article("Två döda i trafikolycka"));
    assertThat(unlisted.zone()).isEqualTo(Zone.RED);
    assertThat(unlisted.topics()).containsExactly(Topic.HOME);
    assertThat(unlisted.reason()).isEqualTo(REASON);
    assertThat(classifier.classify(article("Nytt badrum " + MARKER)).zone()).isEqualTo(Zone.GREEN);
  }

  private static FakeArticleClassifier classifier(Zone zone, String greenMarker) {
    return classifier(zone, greenMarker, DemoClassifications.NONE);
  }

  private static FakeArticleClassifier classifier(
      Zone zone, String greenMarker, DemoClassifications demo) {
    return new FakeArticleClassifier(
        zone, greenMarker, List.of(Topic.HOME), REASON, demo, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static ArticleToClassify article(String headline) {
    return new ArticleToClassify(UUID.randomUUID(), 1, headline, "Text");
  }
}
