package se.rainmaker.contentintent.pipeline;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import se.rainmaker.contentingestion.api.ArticleTexts;
import se.rainmaker.contentintent.api.ArticleClassifier;

/** Wires the classification of stored articles (#209) and its retry (#223). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(RetryProperties.class)
class PipelineConfiguration {

  @Bean
  ArticleClassification articleClassification(
      ClassificationStore store, ClassificationResults results, ArticleClassifier classifier) {
    return new ArticleClassification(store, results, classifier, Clock.systemUTC());
  }

  @Bean
  ClassificationRetry classificationRetry(
      ClassificationStore store,
      ArticleTexts texts,
      ArticleClassification classification,
      RetryProperties settings) {
    return new ClassificationRetry(store, texts, classification, settings, Clock.systemUTC());
  }

  /**
   * The first run is one interval after start-up, so the articles read at start-up get their first
   * try before any retry; the next run is one interval after the previous one ends.
   */
  @Bean
  @ConditionalOnBooleanProperty(
      name = "rainmaker.content-intent.retry.enabled",
      matchIfMissing = true)
  SchedulingConfigurer classificationRetrySchedule(
      ClassificationRetry retry, RetryProperties settings) {
    return tasks ->
        tasks.addFixedDelayTask(
            new FixedDelayTask(retry::retryOnce, settings.interval(), settings.interval()));
  }
}
