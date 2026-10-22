package se.rainmaker.contentintent.classification;

import java.time.Clock;
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
 * locally, the task role in AWS. No key is ever configured here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClassifierProperties.class)
public class ClassifierConfiguration {

  /** The classifier the rest of G2 uses. */
  @Bean
  public ArticleClassifier articleClassifier(ClassifierProperties settings) {
    if (settings.mode() == ClassifierProperties.Mode.FAKE) {
      ClassifierProperties.Fake fake = settings.fake();
      return new FakeArticleClassifier(
          fake.zone(),
          fake.topics(),
          new Reason(fake.reasonSv(), fake.reasonEn()),
          Clock.systemUTC());
    }
    BedrockRuntimeClient client =
        BedrockRuntimeClient.builder()
            .region(Region.of(settings.region()))
            .overrideConfiguration(
                config ->
                    config
                        .apiCallTimeout(settings.timeout())
                        .retryStrategy(AwsRetryStrategy.doNotRetry()))
            .build();
    return new BedrockArticleClassifier(client, settings, Clock.systemUTC());
  }
}
