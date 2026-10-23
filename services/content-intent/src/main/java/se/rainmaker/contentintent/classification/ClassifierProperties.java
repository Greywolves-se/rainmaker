package se.rainmaker.contentintent.classification;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;

/**
 * Settings of the classifier, under {@code rainmaker.content-intent.classifier.*} (#148). The
 * values are checked at start-up, so a wrong setting stops the app instead of misbehaving later.
 *
 * @param mode {@code fake} (no Bedrock, the default for local work and tests) or {@code bedrock}
 * @param region the AWS region Bedrock is called in, e.g. {@code eu-north-1}
 * @param modelId the inference profile to call, e.g. {@code eu.anthropic.claude-haiku-5-5}
 * @param maxTokens most tokens Claude may answer with; the answer is a short JSON
 * @param timeout how long one call may take, including reading the answer
 * @param maxAttempts tries per article for temporary errors (throttling, timeouts), at least 1
 * @param backoff wait before the first retry; it doubles for each further retry
 * @param callsPerMinute most Bedrock calls in any minute; more leave the article unclassified
 * @param dailyTokenCap most tokens per UTC day; when used, articles stay unclassified until the
 *     next day
 * @param fake the answer of the fake classifier, used when {@code mode} is {@code fake}
 */
@ConfigurationProperties(prefix = "rainmaker.content-intent.classifier")
public record ClassifierProperties(
    Mode mode,
    String region,
    String modelId,
    int maxTokens,
    Duration timeout,
    int maxAttempts,
    Duration backoff,
    int callsPerMinute,
    long dailyTokenCap,
    Fake fake) {

  /** Which classifier G2 uses. */
  public enum Mode {
    /** {@link FakeArticleClassifier}: no Bedrock call. */
    FAKE,
    /** {@link BedrockArticleClassifier}: calls Claude on Amazon Bedrock. */
    BEDROCK
  }

  /**
   * The fake classifier's answer.
   *
   * @param zone the zone it gives every article
   * @param topics the topics it gives every article
   * @param reasonSv the reason in Swedish
   * @param reasonEn the reason in English
   */
  public record Fake(Zone zone, List<Topic> topics, String reasonSv, String reasonEn) {}

  /** Checks the values; the Bedrock values only matter in Bedrock mode. */
  public ClassifierProperties {
    if (mode == null) {
      throw new IllegalArgumentException("rainmaker.content-intent.classifier.mode is required");
    }
    if (mode == Mode.BEDROCK) {
      require(region != null && !region.isBlank(), "region is required");
      require(modelId != null && !modelId.isBlank(), "model-id is required");
      require(maxTokens > 0, "max-tokens must be positive");
      require(timeout != null && timeout.isPositive(), "timeout must be positive");
      require(maxAttempts >= 1, "max-attempts must be at least 1");
      require(backoff != null && backoff.isPositive(), "backoff must be positive");
      require(callsPerMinute > 0, "calls-per-minute must be positive");
      require(dailyTokenCap > 0, "daily-token-cap must be positive");
    }
    if (mode == Mode.FAKE) {
      require(fake != null, "fake.* is required in fake mode");
    }
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new IllegalArgumentException("rainmaker.content-intent.classifier: " + problem);
    }
  }
}
