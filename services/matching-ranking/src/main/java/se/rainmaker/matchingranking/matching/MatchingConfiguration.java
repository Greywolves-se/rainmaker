package se.rainmaker.matchingranking.matching;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import se.rainmaker.matchingranking.offers.G3OffersClient;
import se.rainmaker.matchingranking.offers.OffersProperties;

/**
 * Turns on matching's settings, {@code rainmaker.matching-ranking.offers.*} (#149) and {@code
 * rainmaker.matching-ranking.retry.*} (#248), and schedules the retry.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({OffersProperties.class, MatchingRetrySettings.class})
class MatchingConfiguration {

  @Bean
  MatchingRetry matchingRetry(
      G3OffersClient offers, SuggestionStore store, MatchingRetrySettings settings) {
    return new MatchingRetry(offers, store, settings);
  }

  /** The first run starts at start-up, the next one {@code interval} after it ends. */
  @Bean
  @ConditionalOnBooleanProperty(
      name = "rainmaker.matching-ranking.retry.enabled",
      matchIfMissing = true)
  SchedulingConfigurer matchingRetrySchedule(MatchingRetry retry, MatchingRetrySettings settings) {
    return tasks -> tasks.addFixedDelayTask(retry, settings.interval());
  }
}
