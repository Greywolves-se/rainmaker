package se.rainmaker.trackingclick.database;

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
 * S2 Tracking & Click's own database (#155, architecture D16): a connection pool from {@code
 * rainmaker.tracking-click.datasource.*}, Flyway migrations from {@code
 * db/migration/tracking-click} (run at start-up), a {@link JdbcClient} and a transaction manager.
 *
 * <p>An app can hold several modules' databases, so every bean has a module-specific name. Inject
 * the client with {@code @Qualifier(JDBC_CLIENT)} and mark transactions with
 * {@code @Transactional(TRANSACTION_MANAGER)}; an unnamed {@code @Transactional} is ambiguous.
 */
@Configuration(proxyBeanMethods = false)
public class TrackingClickDatabaseConfiguration {

  /** Bean name of this module's {@link JdbcClient}. */
  public static final String JDBC_CLIENT = "trackingClickJdbcClient";

  /** Bean name of this module's transaction manager. */
  public static final String TRANSACTION_MANAGER = "trackingClickTransactionManager";

  private static final String DATA_SOURCE = "trackingClickDataSource";
  private static final String FLYWAY = "trackingClickFlyway";

  @Bean(DATA_SOURCE)
  @ConfigurationProperties("rainmaker.tracking-click.datasource")
  HikariDataSource trackingClickDataSource() {
    return DataSourceBuilder.create().type(HikariDataSource.class).build();
  }

  @Bean(name = FLYWAY, initMethod = "migrate")
  Flyway trackingClickFlyway(@Qualifier(DATA_SOURCE) DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/tracking-click")
        .load();
  }

  @Bean(JDBC_CLIENT)
  @DependsOn(FLYWAY)
  JdbcClient trackingClickJdbcClient(@Qualifier(DATA_SOURCE) DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  @Bean(TRANSACTION_MANAGER)
  PlatformTransactionManager trackingClickTransactionManager(
      @Qualifier(DATA_SOURCE) DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }
}
