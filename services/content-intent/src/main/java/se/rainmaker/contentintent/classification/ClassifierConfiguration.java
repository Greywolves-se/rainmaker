package se.rainmaker.contentintent.classification;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
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

  private static final String DEMO_SETTING =
      "rainmaker.content-intent.classifier.fake.classifications";

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
      ClassifierProperties settings,
      ObjectProvider<BedrockRuntimeClient> bedrock,
      ResourceLoader resources) {
    if (settings.mode() == ClassifierProperties.Mode.FAKE) {
      ClassifierProperties.Fake fake = settings.fake();
      DemoClassifications demo = demoClassifications(fake.classifications(), resources);
      // Not Claude: say so where everyone looks, with the zone the articles will get.
      LOG.atWarn()
          .addKeyValue("zone", fake.zone().key())
          .addKeyValue("green_marker", fake.greenMarker())
          .addKeyValue("demo_classifications", fake.classifications())
          .log(
              "Fake classifier active: articles are classified {}, not by Claude{}{}",
              fake.zone().key(),
              demo.size() == 0
                  ? ""
                  : "; "
                      + demo.size()
                      + " headlines from "
                      + fake.classifications()
                      + " get the demo file's answers",
              fake.greenMarker().isEmpty()
                  ? ""
                  : "; headlines containing \"" + fake.greenMarker() + "\" are green");
      return new FakeArticleClassifier(
          fake.zone(),
          fake.greenMarker(),
          fake.topics(),
          new Reason(fake.reasonSv(), fake.reasonEn()),
          demo,
          Clock.systemUTC());
    }
    return new BedrockArticleClassifier(bedrock.getObject(), settings, Clock.systemUTC());
  }

  /** Reads the demo file once at start-up; a missing or broken one stops G2 (#261). */
  private static DemoClassifications demoClassifications(
      String location, ResourceLoader resources) {
    if (location.isEmpty()) {
      return DemoClassifications.NONE;
    }
    try (InputStream json = resources.getResource(location).getInputStream()) {
      return DemoClassifications.read(json, location);
    } catch (IOException notReadable) {
      throw new IllegalStateException(DEMO_SETTING + ": can't read " + location, notReadable);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException(DEMO_SETTING + ": " + invalid.getMessage(), invalid);
    }
  }
}
