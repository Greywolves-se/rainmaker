package se.rainmaker.contentintent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The classification types follow the contract (#145, sections 3.2 and 3.3). */
class ClassificationTest {

  private static final Instant AT = Instant.parse("2026-10-30T09:15:04Z");
  private static final Reason REASON = new Reason("Praktiska råd.", "Practical advice.");

  @Test
  void keysAreTheContractValues() {
    assertThat(List.of(Topic.values()))
        .extracting(Topic::key)
        .containsExactly(
            "travel",
            "outdoor",
            "sport",
            "culture",
            "food",
            "home",
            "cars",
            "health",
            "personal-finance",
            "other");
    assertThat(List.of(Zone.values()))
        .extracting(Zone::key)
        .containsExactly("green", "yellow", "red");
    assertThat(List.of(ClassificationStatus.values()))
        .extracting(ClassificationStatus::key)
        .containsExactly("unclassified", "classified", "failed");
  }

  @Test
  void findsTopicsAndZonesByKeyOnly() {
    assertThat(Topic.fromKey("personal-finance")).contains(Topic.PERSONAL_FINANCE);
    assertThat(Topic.fromKey("PERSONAL_FINANCE")).isEmpty();
    assertThat(Topic.fromKey("politics")).isEmpty();
    assertThat(Zone.fromKey("red")).contains(Zone.RED);
    assertThat(Zone.fromKey("orange")).isEmpty();
  }

  @Test
  void classifiedCarriesZoneTopicsReasonModelAndTime() {
    Classification classification =
        Classification.classified(Zone.GREEN, List.of(Topic.HOME), REASON, "model-x", AT);

    assertThat(classification.status()).isEqualTo(ClassificationStatus.CLASSIFIED);
    assertThat(classification.zone()).isEqualTo(Zone.GREEN);
    assertThat(classification.topics()).containsExactly(Topic.HOME);
    assertThat(classification.reason()).isEqualTo(REASON);
    assertThat(classification.model()).isEqualTo("model-x");
    assertThat(classification.classifiedAt()).isEqualTo(AT);
  }

  @Test
  void failedAndUnclassifiedHaveNoZoneTopicsOrReason() {
    Classification failed = Classification.failed("model-x", AT);
    Classification unclassified = Classification.unclassified();

    for (Classification classification : List.of(failed, unclassified)) {
      assertThat(classification.zone()).isNull();
      assertThat(classification.topics()).isEmpty();
      assertThat(classification.reason()).isNull();
    }
    assertThat(failed.status()).isEqualTo(ClassificationStatus.FAILED);
    assertThat(failed.model()).isEqualTo("model-x");
    assertThat(unclassified.status()).isEqualTo(ClassificationStatus.UNCLASSIFIED);
    assertThat(unclassified.model()).isNull();
    assertThat(unclassified.classifiedAt()).isNull();
  }

  /** D-4: only a classified green article may get suggestions; everything else is like red. */
  @Test
  void onlyClassifiedGreenMayGetSuggestions() {
    assertThat(
            Classification.classified(Zone.GREEN, List.of(Topic.HOME), REASON, "m", AT)
                .mayGetSuggestions())
        .isTrue();
    assertThat(
            Classification.classified(Zone.YELLOW, List.of(Topic.HEALTH), REASON, "m", AT)
                .mayGetSuggestions())
        .isFalse();
    assertThat(
            Classification.classified(Zone.RED, List.of(Topic.OTHER), REASON, "m", AT)
                .mayGetSuggestions())
        .isFalse();
    assertThat(Classification.failed("m", AT).mayGetSuggestions()).isFalse();
    assertThat(Classification.unclassified().mayGetSuggestions()).isFalse();
  }

  @Test
  void classifiedNeedsOneToThreeTopics() {
    assertThatThrownBy(() -> Classification.classified(Zone.GREEN, List.of(), REASON, "m", AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Classification.classified(
                    Zone.GREEN,
                    List.of(Topic.HOME, Topic.FOOD, Topic.TRAVEL, Topic.CARS),
                    REASON,
                    "m",
                    AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reasonNeedsBothLanguages() {
    assertThatThrownBy(() -> new Reason("Text", " ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Reason(null, "Text")).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(Zone.class)
  void classifiedNeedsReasonAndModel(Zone zone) {
    assertThatThrownBy(() -> Classification.classified(zone, List.of(Topic.OTHER), null, "m", AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Classification.classified(zone, List.of(Topic.OTHER), REASON, " ", AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void articleToClassifyNeedsHeadlineTextAndVersion() {
    UUID id = UUID.randomUUID();

    assertThat(new ArticleToClassify(id, 1, "Rubrik", "Text").version()).isEqualTo(1);
    assertThatThrownBy(() -> new ArticleToClassify(id, 0, "Rubrik", "Text"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArticleToClassify(id, 1, " ", "Text"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArticleToClassify(id, 1, "Rubrik", ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArticleToClassify(null, 1, "Rubrik", "Text"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
