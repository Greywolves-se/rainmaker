package se.rainmaker.publishercontrol.articles;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

/** Turns on the settings for G2's articles API and polls it on a schedule (#150, contract 7.1). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ArticlesProperties.class)
class ArticlesConfiguration {

  /**
   * The first poll runs at start-up, the next one {@code poll-interval} after it ends. G2 may start
   * later than G3 (contract 7: start in any order); until it does, each poll logs and gives up.
   */
  @Bean
  @ConditionalOnBooleanProperty(
      name = "rainmaker.publisher-control.articles.polling",
      matchIfMissing = true)
  SchedulingConfigurer articlesPolling(ArticleSync sync, ArticlesProperties properties) {
    return tasks -> tasks.addFixedDelayTask(sync::pollOnce, properties.pollInterval());
  }
}
