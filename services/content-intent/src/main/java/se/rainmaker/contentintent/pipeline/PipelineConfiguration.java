package se.rainmaker.contentintent.pipeline;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import se.rainmaker.contentingestion.api.LatestArticleTexts;
import se.rainmaker.contentintent.api.ArticleClassifier;

/** Wires the classification of stored articles (#209) and its retry (#223). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(RetrySettings.class)
class PipelineConfiguration {

  @Bean
  ArticleClassification articleClassification(
      ClassificationStore store, ClassificationResults results, ArticleClassifier classifier) {
    return new ArticleClassification(store, results, classifier, Clock.systemUTC());
  }

  @Bean
  ClassificationRetry classificationRetry(
      ClassificationStore store,
      LatestArticleTexts articles,
      ArticleClassification classification,
      RetrySettings settings) {
    return new ClassificationRetry(store, articles, classification, settings);
  }

  /** The first run starts at start-up, the next one {@code interval} after it ends. */
  @Bean
  @ConditionalOnBooleanProperty(
      name = "rainmaker.content-intent.retry.enabled",
      matchIfMissing = true)
  SchedulingConfigurer classificationRetrySchedule(
      ClassificationRetry retry, RetrySettings settings) {
    return tasks -> tasks.addFixedDelayTask(retry, settings.interval());
  }
}
