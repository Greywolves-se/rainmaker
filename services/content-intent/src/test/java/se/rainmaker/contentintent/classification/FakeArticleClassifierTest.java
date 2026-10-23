package se.rainmaker.contentintent.classification;

import static org.assertj.core.api.Assertions.assertThat;

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

  @Test
  void answersEveryArticleWithTheConfiguredClassification() {
    Reason reason = new Reason("Låtsasklassificering.", "Fake classification.");
    FakeArticleClassifier classifier =
        new FakeArticleClassifier(
            Zone.GREEN, List.of(Topic.HOME), reason, Clock.fixed(NOW, ZoneOffset.UTC));

    Classification classification =
        classifier.classify(new ArticleToClassify(UUID.randomUUID(), 1, "Rubrik", "Text"));

    assertThat(classification.status()).isEqualTo(ClassificationStatus.CLASSIFIED);
    assertThat(classification.zone()).isEqualTo(Zone.GREEN);
    assertThat(classification.topics()).containsExactly(Topic.HOME);
    assertThat(classification.reason()).isEqualTo(reason);
    assertThat(classification.model()).isEqualTo(FakeArticleClassifier.MODEL);
    assertThat(classification.classifiedAt()).isEqualTo(NOW);
  }
}
