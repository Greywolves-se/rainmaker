package se.rainmaker.trackingclick.database;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
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
 *
 * <p>A reader who opens a click link waits for this database on a blank page (#192), so it never
 * waits long: at most {@code connection-timeout} for a connection (1 s unless set, never over 2 s,
 * checked at start-up), then at most {@link #STATEMENT_TIMEOUT_SECONDS} for the statement. After
 * that the click isn't stored and the reader gets the 503 page.
 */
@Configuration(proxyBeanMethods = false)
public class TrackingClickDatabaseConfiguration {

  /** Bean name of this module's {@link JdbcClient}. */
  public static final String JDBC_CLIENT = "trackingClickJdbcClient";

  /** Bean name of this module's transaction manager. */
  public static final String TRANSACTION_MANAGER = "trackingClickTransactionManager";

  /** How long a statement may run; JDBC counts it in whole seconds. */
  static final int STATEMENT_TIMEOUT_SECONDS = 1;

  /** The wait for a connection when {@code connection-timeout} isn't set; HikariCP's is 30 s. */
  private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(1);

  /** The longest {@code connection-timeout} G1 starts with. HikariCP refuses under 250 ms. */
  private static final Duration MAX_CONNECTION_TIMEOUT = Duration.ofSeconds(2);

  private static final String DATA_SOURCE = "trackingClickDataSource";
  private static final String FLYWAY = "trackingClickFlyway";

  @Bean(DATA_SOURCE)
  @ConfigurationProperties("rainmaker.tracking-click.datasource")
  HikariDataSource trackingClickDataSource() {
    HikariDataSource pool = DataSourceBuilder.create().type(HikariDataSource.class).build();
    // Set before the settings are bound, so connection-timeout overrides it.
    pool.setConnectionTimeout(CONNECTION_TIMEOUT.toMillis());
    return pool;
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
  JdbcClient trackingClickJdbcClient(@Qualifier(DATA_SOURCE) HikariDataSource dataSource) {
    requireShortConnectionTimeout(dataSource);
    JdbcTemplate statements = new JdbcTemplate(dataSource);
    statements.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
    return JdbcClient.create(statements);
  }

  @Bean(TRANSACTION_MANAGER)
  PlatformTransactionManager trackingClickTransactionManager(
      @Qualifier(DATA_SOURCE) DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  /** HikariCP reads 0 as "wait forever", so it's refused like any other long wait. */
  private static void requireShortConnectionTimeout(HikariDataSource dataSource) {
    if (dataSource.getConnectionTimeout() > MAX_CONNECTION_TIMEOUT.toMillis()) {
      throw new IllegalStateException(
          "rainmaker.tracking-click.datasource.connection-timeout must be at most "
              + MAX_CONNECTION_TIMEOUT.toMillis()
              + " ms (and not 0), because a reader opening a click link waits for it (#192)");
    }
  }
}
