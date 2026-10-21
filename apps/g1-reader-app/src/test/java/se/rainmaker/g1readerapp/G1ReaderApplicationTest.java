package se.rainmaker.g1readerapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import se.rainmaker.trackingclick.database.TrackingClickDatabaseConfiguration;

/** The app starts against a real PostgreSQL set up exactly like the local one (#155). */
@SpringBootTest
@Testcontainers
class G1ReaderApplicationTest {

  /** The same script docker-compose.yml runs: one database and one login per module. */
  private static final Path INIT_SCRIPT =
      Path.of("../../docker/postgres/init/01-rainmaker-databases.sql");

  private static final String LOCAL_PASSWORD = "local-dev-only";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18")
          .withCopyFileToContainer(
              MountableFile.forHostPath(INIT_SCRIPT),
              "/docker-entrypoint-initdb.d/01-rainmaker-databases.sql");

  @DynamicPropertySource
  static void databases(DynamicPropertyRegistry registry) {
    database(registry, "tracking-click");
  }

  private static void database(DynamicPropertyRegistry registry, String module) {
    String prefix = "rainmaker." + module + ".datasource.";
    registry.add(prefix + "jdbc-url", () -> url(module.replace('-', '_')));
    registry.add(prefix + "password", () -> LOCAL_PASSWORD);
  }

  private static String url(String database) {
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(5432)
        + "/"
        + database;
  }

  @Value("${server.port}")
  private int port;

  @Autowired
  @Qualifier(TrackingClickDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient trackingClick;

  @Test
  void startsOnItsOwnPort() {
    assertThat(port).isEqualTo(8081);
  }

  @Test
  void everyModuleDatabaseHasItsFirstMigration() {
    assertThat(latestMigration(trackingClick)).isEqualTo("1");
  }

  @Test
  void moduleLoginCannotOpenAnotherModulesDatabase() {
    assertThatThrownBy(
            () ->
                DriverManager.getConnection(
                    url("content_ingestion"), "tracking_click", LOCAL_PASSWORD))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("permission denied");
  }

  private static String latestMigration(JdbcClient database) {
    return database
        .sql("SELECT max(version) FROM flyway_schema_history WHERE success")
        .query(String.class)
        .single();
  }
}
