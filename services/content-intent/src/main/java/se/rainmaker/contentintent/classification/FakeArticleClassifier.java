package se.rainmaker.contentintent.classification;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
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
 *
 * <p>Two exceptions, both off by default:
 *
 * <ul>
 *   <li>a headline that the demo file lists gets that file's answer (#261), so a local demo shows
 *       the test site's articles in their own zones, with their own topics;
 *   <li>otherwise, a headline that contains the green marker is green, for the end-to-end test
 *       (#154), which needs a green and a red article in one run.
 * </ul>
 */
public class FakeArticleClassifier implements ArticleClassifier {

  /** The model name stored with a fake classification, so it is never mistaken for Claude's. */
  public static final String MODEL = "fake-classifier";

  private final Zone zone;
  private final String greenMarker;
  private final List<Topic> topics;
  private final Reason reason;
  private final DemoClassifications demo;
  private final Clock clock;

  /**
   * Creates the classifier with the answer it gives every article.
   *
   * @param greenMarker a headline that contains it is green instead of {@code zone}; blank for none
   * @param demo answers for listed headlines, before the marker; {@link DemoClassifications#NONE}
   *     for none
   */
  FakeArticleClassifier(
      Zone zone,
      String greenMarker,
      List<Topic> topics,
      Reason reason,
      DemoClassifications demo,
      Clock clock) {
    this.zone = zone;
    this.greenMarker = greenMarker.strip();
    this.topics = List.copyOf(topics);
    this.reason = reason;
    this.demo = demo;
    this.clock = clock;
  }

  @Override
  public Classification classify(ArticleToClassify article) {
    Optional<DemoClassifications.Answer> listed = demo.find(article.headline());
    if (listed.isPresent()) {
      DemoClassifications.Answer answer = listed.get();
      return Classification.classified(
          answer.zone(), answer.topics(), answer.reason(), MODEL, clock.instant());
    }
    boolean marked = !greenMarker.isEmpty() && article.headline().contains(greenMarker);
    return Classification.classified(
        marked ? Zone.GREEN : zone, topics, reason, MODEL, clock.instant());
  }
}
