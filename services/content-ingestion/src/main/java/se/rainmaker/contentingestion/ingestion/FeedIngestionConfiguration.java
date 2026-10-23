package se.rainmaker.contentingestion.ingestion;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import se.rainmaker.contentingestion.database.ContentIngestionDatabaseConfiguration;
import se.rainmaker.contentingestion.feed.RssFeedParser;
import se.rainmaker.contentingestion.page.ArticlePageParser;
import se.rainmaker.contentingestion.site.PublisherSite;

/**
 * Wires feed ingestion (#147) and polls the feed on a schedule. Each stored version is published as
 * a Spring event, {@link se.rainmaker.contentingestion.api.ArticleStored}, for content-intent.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(FeedSettings.class)
class FeedIngestionConfiguration {

  @Bean
  FeedIngestion feedIngestion(
      FeedSettings settings,
      @Qualifier(ContentIngestionDatabaseConfiguration.JDBC_CLIENT) JdbcClient database,
      ApplicationEventPublisher events) {
    return new FeedIngestion(
        settings.url(),
        new PublisherSite(settings.timeout()),
        new RssFeedParser(),
        new ArticlePageParser(),
        new ArticleVersions(database),
        events::publishEvent);
  }

  /** The first poll runs at start-up, the next one {@code poll-interval} after it ends. */
  @Bean
  @ConditionalOnBooleanProperty(
      name = "rainmaker.content-ingestion.feed.polling",
      matchIfMissing = true)
  SchedulingConfigurer feedPolling(FeedIngestion ingestion, FeedSettings settings) {
    return tasks -> tasks.addFixedDelayTask(ingestion::pollOnce, settings.pollInterval());
  }
}
