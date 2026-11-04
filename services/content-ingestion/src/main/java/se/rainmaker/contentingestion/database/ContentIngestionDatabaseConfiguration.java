package se.rainmaker.contentingestion.database;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * S5 Content Ingestion's own database (#155, architecture D16): a connection pool from {@code
 * rainmaker.content-ingestion.datasource.*}, Flyway migrations from {@code
 * db/migration/content-ingestion} (run at start-up), a {@link JdbcClient} and a transaction
 * manager.
 *
 * <p>An app can hold several modules' databases, so every bean has a module-specific name. Inject
 * the client with {@code @Qualifier(JDBC_CLIENT)} and mark transactions with
 * {@code @Transactional(TRANSACTION_MANAGER)}; an unnamed {@code @Transactional} is ambiguous.
 */
@Configuration(proxyBeanMethods = false)
public class ContentIngestionDatabaseConfiguration {

  /** Bean name of this module's {@link JdbcClient}. */
  public static final String JDBC_CLIENT = "contentIngestionJdbcClient";

  /** Bean name of this module's transaction manager. */
  public static final String TRANSACTION_MANAGER = "contentIngestionTransactionManager";

  private static final String DATA_SOURCE = "contentIngestionDataSource";
  private static final String FLYWAY = "contentIngestionFlyway";

  @Bean(DATA_SOURCE)
  @ConfigurationProperties("rainmaker.content-ingestion.datasource")
  HikariDataSource contentIngestionDataSource() {
    return DataSourceBuilder.create().type(HikariDataSource.class).build();
  }

  @Bean(name = FLYWAY, initMethod = "migrate")
  Flyway contentIngestionFlyway(@Qualifier(DATA_SOURCE) DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/content-ingestion")
        .load();
  }

  @Bean(JDBC_CLIENT)
  @DependsOn(FLYWAY)
  JdbcClient contentIngestionJdbcClient(@Qualifier(DATA_SOURCE) DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  @Bean(TRANSACTION_MANAGER)
  PlatformTransactionManager contentIngestionTransactionManager(
      @Qualifier(DATA_SOURCE) DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }
}
