package se.rainmaker.contentingestion.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The module's own database setup runs its migrations, including #147's article table. */
@Testcontainers
class ContentIngestionDatabaseConfigurationTest {

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @Test
  void migratesTheModulesOwnDatabase() {
    ContentIngestionDatabaseConfiguration configuration =
        new ContentIngestionDatabaseConfiguration();
    try (HikariDataSource dataSource = configuration.contentIngestionDataSource()) {
      dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
      dataSource.setUsername(POSTGRES.getUsername());
      dataSource.setPassword(POSTGRES.getPassword());

      configuration.contentIngestionFlyway(dataSource).migrate();
      JdbcClient database = configuration.contentIngestionJdbcClient(dataSource);

      assertThat(database.sql("SELECT count(*) FROM article_version").query(Long.class).single())
          .isZero();
      assertThat(configuration.contentIngestionTransactionManager(dataSource)).isNotNull();
    }
  }
}
