package se.rainmaker.g2contentapp;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * One real PostgreSQL for all of G2's tests, set up exactly like the local one (#155): the same
 * init script as docker-compose.yml, so one database and one login per module. It starts once and
 * is shared, so every test class sees the same container (Testcontainers removes it afterwards).
 */
final class TestDatabases {

  /** The password of every module login on a developer's machine. */
  static final String LOCAL_PASSWORD = "local-dev-only";

  private static final PostgreSQLContainer POSTGRES = startPostgres();

  private TestDatabases() {}

  /** Points G2's module databases at the shared container. */
  static void register(DynamicPropertyRegistry registry) {
    database(registry, "content-ingestion");
    database(registry, "content-intent");
    database(registry, "matching-ranking");
  }

  /** The JDBC URL of one database, e.g. {@code matching_ranking}. */
  static String url(String database) {
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(5432)
        + "/"
        + database;
  }

  private static void database(DynamicPropertyRegistry registry, String module) {
    String prefix = "rainmaker." + module + ".datasource.";
    registry.add(prefix + "jdbc-url", () -> url(module.replace('-', '_')));
    registry.add(prefix + "password", () -> LOCAL_PASSWORD);
  }

  private static PostgreSQLContainer startPostgres() {
    PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:18")
            .withCopyFileToContainer(
                MountableFile.forHostPath(initScript()),
                "/docker-entrypoint-initdb.d/01-rainmaker-databases.sql");
    postgres.start();
    return postgres;
  }

  /**
   * The init script, found by walking up from the working directory, so the tests run the same from
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
