package se.rainmaker.contentintent.classification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.rainmaker.contentintent.api.ArticleClassifier;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * The setting {@code rainmaker.content-intent.classifier.mode} picks the classifier; no AWS call is
 * made when the app starts, whichever mode it is in.
 */
@ExtendWith(OutputCaptureExtension.class)
class ClassifierConfigurationTest {

  private static final String PREFIX = "rainmaker.content-intent.classifier.";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(ClassifierConfiguration.class)
          .withPropertyValues(
              PREFIX + "region=eu-north-1",
              PREFIX + "model-id=eu.anthropic.claude-haiku-5-5",
              PREFIX + "max-tokens=400",
              PREFIX + "max-text-chars=20000",
              PREFIX + "timeout=20s",
              PREFIX + "max-attempts=3",
              PREFIX + "backoff=1s",
              PREFIX + "calls-per-minute=20",
              PREFIX + "daily-token-cap=200000",
              PREFIX + "fake.zone=green",
              PREFIX + "fake.topics=home,personal-finance",
              PREFIX + "fake.reason-sv=Låtsas.",
              PREFIX + "fake.reason-en=Fake.");

  @Test
  void usesTheFakeClassifierInFakeMode() {
    runner
        .withPropertyValues(PREFIX + "mode=fake")
        .run(
            context ->
                assertThat(context.getBean(ArticleClassifier.class))
                    .isInstanceOf(FakeArticleClassifier.class));
  }

  @Test
  void usesBedrockInBedrockModeWithoutCallingAws() {
    runner
        .withPropertyValues(PREFIX + "mode=bedrock")
        .run(
            context ->
                assertThat(context.getBean(ArticleClassifier.class))
                    .isInstanceOf(BedrockArticleClassifier.class));
  }

  /** Anyone reading the log can see that classifications aren't Claude's, and what they all are. */
  @Test
  void warnsAtStartUpThatTheFakeClassifierIsActive(CapturedOutput output) {
    runner
        .withPropertyValues(PREFIX + "mode=fake")
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains("WARN").contains("Fake classifier active").contains("green");
  }

  /** As a bean of its own, the SDK client is closed when the app shuts down. */
  @Test
  void makesTheBedrockClientBeanOnlyInBedrockMode() {
    runner
        .withPropertyValues(PREFIX + "mode=bedrock")
        .run(context -> assertThat(context).hasSingleBean(BedrockRuntimeClient.class));
    runner
        .withPropertyValues(PREFIX + "mode=fake")
        .run(context -> assertThat(context).doesNotHaveBean(BedrockRuntimeClient.class));
  }

  @Test
  void refusesToStartInBedrockModeWithoutTextLimit() {
    runner
        .withPropertyValues(PREFIX + "mode=bedrock", PREFIX + "max-text-chars=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void refusesToStartWithoutMode() {
    runner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void refusesToStartInBedrockModeWithoutLimit() {
    runner
        .withPropertyValues(PREFIX + "mode=bedrock", PREFIX + "calls-per-minute=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
