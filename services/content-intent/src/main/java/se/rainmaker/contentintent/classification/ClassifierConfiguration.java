package se.rainmaker.contentintent.classification;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.rainmaker.contentintent.api.ArticleClassifier;
import se.rainmaker.contentintent.api.Reason;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Chooses the classifier from {@code rainmaker.content-intent.classifier.mode} (#148): the fake one
 * (default; no AWS needed) or Bedrock.
 *
 * <p>In Bedrock mode the SDK's own retries are switched off, so {@link BedrockArticleClassifier}
 * alone decides how often Bedrock is called, and every call has the configured timeout. Credentials
 * come from the environment through the SDK's default chain: an SSO login ({@code AWS_PROFILE})
 * locally, the task role in AWS. No key is ever configured here. The SDK client is a bean of its
 * own, so Spring closes it when the app shuts down.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClassifierProperties.class)
public class ClassifierConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ClassifierConfiguration.class);

  /** The Bedrock client, only in Bedrock mode. Building it makes no AWS call. */
  @Bean
  @ConditionalOnProperty(name = "rainmaker.content-intent.classifier.mode", havingValue = "bedrock")
  public BedrockRuntimeClient bedrockRuntimeClient(ClassifierProperties settings) {
    return BedrockRuntimeClient.builder()
        .region(Region.of(settings.region()))
        .overrideConfiguration(
            config ->
                config
                    .apiCallTimeout(settings.timeout())
                    .retryStrategy(AwsRetryStrategy.doNotRetry()))
        .build();
  }

  /** The classifier the rest of G2 uses. */
  @Bean
  public ArticleClassifier articleClassifier(
      ClassifierProperties settings, ObjectProvider<BedrockRuntimeClient> bedrock) {
    if (settings.mode() == ClassifierProperties.Mode.FAKE) {
      ClassifierProperties.Fake fake = settings.fake();
      // Not Claude: say so where everyone looks, with the zone every article will get.
      LOG.atWarn()
          .addKeyValue("zone", fake.zone().key())
          .log(
              "Fake classifier active: every article is classified {}, not by Claude",
              fake.zone().key());
      return new FakeArticleClassifier(
          fake.zone(),
          fake.topics(),
          new Reason(fake.reasonSv(), fake.reasonEn()),
          Clock.systemUTC());
    }
    return new BedrockArticleClassifier(bedrock.getObject(), settings, Clock.systemUTC());
  }
}
