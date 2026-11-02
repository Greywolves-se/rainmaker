package se.rainmaker.trackingclick.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * How long a reader opening a click link can wait for S2's database (#192): the 503 page must come
 * quickly when PostgreSQL is down or hangs, not after HikariCP's default 30 seconds.
 */
class TrackingClickDatabaseConfigurationTest {

  /** Far below HikariCP's default 30 s, far above the 1 s the settings give: no flaky margin. */
  private static final Duration READER_GIVES_UP = Duration.ofSeconds(10);

  private final TrackingClickDatabaseConfiguration configuration =
      new TrackingClickDatabaseConfiguration();

  @Test
  void waitsOneSecondForConnectionUnlessSetOtherwise() {
    try (HikariDataSource pool = configuration.trackingClickDataSource()) {
      assertThat(pool.getConnectionTimeout()).isEqualTo(1000);
    }
  }

  @Test
  void refusesToStartWithConnectionTimeoutLongerThanTwoSeconds() {
    try (HikariDataSource pool = configuration.trackingClickDataSource()) {
      pool.setConnectionTimeout(30_000);

      assertThatThrownBy(() -> configuration.trackingClickJdbcClient(pool))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("rainmaker.tracking-click.datasource.connection-timeout");
    }
  }

  /** HikariCP reads 0 as "wait forever". */
  @Test
  void refusesToStartWithConnectionTimeoutOfZero() {
    try (HikariDataSource pool = configuration.trackingClickDataSource()) {
      pool.setConnectionTimeout(0);

      assertThatThrownBy(() -> configuration.trackingClickJdbcClient(pool))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  /** PostgreSQL stops after G1 started: the click can't be stored, and that's known quickly. */
  @Test
  void givesUpQuicklyWhenTheDatabaseGoesDown() {
    try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
        HikariDataSource pool = configuration.trackingClickDataSource()) {
      postgres.start();
      JdbcClient database = connected(pool, postgres);
      assertThat(database.sql("SELECT 1").query(Integer.class).single()).isEqualTo(1);

      postgres.stop();

      // The first request finds the pooled connection broken; the next has to wait for a new one.
      assertTimeoutPreemptively(
          READER_GIVES_UP,
          () -> {
            for (int request = 1; request <= 2; request++) {
              assertThatThrownBy(() -> database.sql("SELECT 1").query(Integer.class).single())
                  .isInstanceOf(DataAccessException.class);
            }
          });
    }
  }

  /** The database answers connections but a statement hangs, e.g. behind a lock. */
  @Test
  void stopsStatementThatHangs() {
    try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
        HikariDataSource pool = configuration.trackingClickDataSource()) {
      postgres.start();
      JdbcClient database = connected(pool, postgres);

      assertTimeoutPreemptively(
          READER_GIVES_UP,
          () ->
              assertThatThrownBy(() -> database.sql("SELECT pg_sleep(30)").query().singleValue())
                  .isInstanceOf(DataAccessException.class));
    }
  }

  /** The module's client on this pool, as G1 builds it. */
  private JdbcClient connected(HikariDataSource pool, PostgreSQLContainer postgres) {
    pool.setJdbcUrl(postgres.getJdbcUrl());
    pool.setUsername(postgres.getUsername());
    pool.setPassword(postgres.getPassword());
    return configuration.trackingClickJdbcClient(pool);
  }
}
