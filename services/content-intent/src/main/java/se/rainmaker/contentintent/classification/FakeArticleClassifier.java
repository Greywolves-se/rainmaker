package se.rainmaker.contentintent.classification;

import java.time.Clock;
import java.util.List;
import se.rainmaker.contentintent.api.ArticleClassifier;
import se.rainmaker.contentintent.api.ArticleToClassify;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;

/**
 * Gives every article the same configured classification, without calling Bedrock (#148, Getting
 * Started Guide 5.3). For local work before Bedrock access and for tests; never for the demo's real
 * classification.
 */
public class FakeArticleClassifier implements ArticleClassifier {

  /** The model name stored with a fake classification, so it is never mistaken for Claude's. */
  public static final String MODEL = "fake-classifier";

  private final Zone zone;
  private final List<Topic> topics;
  private final Reason reason;
  private final Clock clock;

  /** Creates the classifier with the answer it gives every article. */
  public FakeArticleClassifier(Zone zone, List<Topic> topics, Reason reason, Clock clock) {
    this.zone = zone;
    this.topics = List.copyOf(topics);
    this.reason = reason;
    this.clock = clock;
  }

  @Override
  public Classification classify(ArticleToClassify article) {
    return Classification.classified(zone, topics, reason, MODEL, clock.instant());
  }
}
