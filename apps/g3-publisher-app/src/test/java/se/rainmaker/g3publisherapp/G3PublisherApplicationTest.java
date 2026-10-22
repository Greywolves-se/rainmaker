package se.rainmaker.g3publisherapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
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
import se.rainmaker.advertisermarketplace.database.AdvertiserMarketplaceDatabaseConfiguration;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;

/** The app starts against a real PostgreSQL set up exactly like the local one (#155). */
@SpringBootTest
@Testcontainers
class G3PublisherApplicationTest {

  /** The same script docker-compose.yml runs: one database and one login per module. */
  private static final Path INIT_SCRIPT = initScript();

  private static final String LOCAL_PASSWORD = "local-dev-only";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18")
          .withCopyFileToContainer(
              MountableFile.forHostPath(INIT_SCRIPT),
              "/docker-entrypoint-initdb.d/01-rainmaker-databases.sql");

  @DynamicPropertySource
  static void databases(DynamicPropertyRegistry registry) {
    database(registry, "publisher-control");
    database(registry, "advertiser-marketplace");
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
  @Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient publisherControl;

  @Autowired
  @Qualifier(AdvertiserMarketplaceDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient advertiserMarketplace;

  @Test
  void startsOnItsOwnPort() {
    assertThat(port).isEqualTo(8083);
  }

  @Test
  void everyModuleDatabaseHasItsFirstMigration() {
    assertThat(firstMigrationRuns(publisherControl)).isEqualTo(1L);
    assertThat(firstMigrationRuns(advertiserMarketplace)).isEqualTo(1L);
  }

  @Test
  void moduleLoginCannotOpenAnotherModulesDatabase() {
    assertThatThrownBy(
            () ->
                DriverManager.getConnection(
                    url("content_ingestion"), "publisher_control", LOCAL_PASSWORD))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("permission denied");
  }

  /** How many times V1 is recorded as applied: 1 when it ran, whatever migrations follow it. */
  private static long firstMigrationRuns(JdbcClient database) {
    return database
        .sql("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")
        .query(Long.class)
        .single();
  }

  /**
   * The init script, found by walking up from the working directory, so the test runs the same from
   * Maven (the module folder), the repository root or an IDE.
   */
  private static Path initScript() {
    Path script = Path.of("docker", "postgres", "init", "01-rainmaker-databases.sql");
    for (Path folder = Path.of("").toAbsolutePath(); folder != null; folder = folder.getParent()) {
      if (Files.isRegularFile(folder.resolve(script))) {
        return folder.resolve(script);
      }
    }
    throw new IllegalStateException(
        "Cannot find " + script + " above " + Path.of("").toAbsolutePath());
  }
}
