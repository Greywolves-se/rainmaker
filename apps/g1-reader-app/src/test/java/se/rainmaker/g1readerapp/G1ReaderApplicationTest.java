package se.rainmaker.g1readerapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import se.rainmaker.trackingclick.database.TrackingClickDatabaseConfiguration;

/**
 * The app starts against a real PostgreSQL set up exactly like the local one (#155), and serves
 * rainmaker.js (#152). It runs as on a developer's machine, with the local profile (#153).
 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Testcontainers
class G1ReaderApplicationTest {

  /** The same script docker-compose.yml runs: one database and one login per module. */
  private static final Path INIT_SCRIPT = initScript();

  private static final String LOCAL_PASSWORD = "local-dev-only";

  private static final String WIDGET_API = "/widget/v1/links";
  private static final String ARTICLE = "http://localhost:3000/artikel/badrumsrenovering";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18")
          .withCopyFileToContainer(
              MountableFile.forHostPath(INIT_SCRIPT),
              "/docker-entrypoint-initdb.d/01-rainmaker-databases.sql");

  /** A stand-in for widget/dist/rainmaker.js, which the Java build doesn't build. */
  private static final Path WIDGET_SCRIPT = widgetScript();

  @DynamicPropertySource
  static void databases(DynamicPropertyRegistry registry) {
    database(registry, "tracking-click");
    registry.add(
        "rainmaker.widget-delivery.script-location", () -> WIDGET_SCRIPT.toUri().toString());
    // No G3 here, not even one a developer has running on 8083: port 1 refuses every connection.
    registry.add("rainmaker.widget-delivery.approved-links.base-url", () -> "http://127.0.0.1:1");
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

  @Autowired private MockMvc mockMvc;

  @Test
  void startsOnItsOwnPort() {
    assertThat(port).isEqualTo(8081);
  }

  @Test
  void servesTheWidgetScript() throws Exception {
    mockMvc
        .perform(get("/widget/rainmaker.js"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/javascript;charset=UTF-8"));
  }

  @Test
  void everyModuleDatabaseHasItsFirstMigration() {
    assertThat(firstMigrationRuns(trackingClick)).isEqualTo(1L);
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

  /** The fake-news site's pages may call the widget API (#151, contract 7.4), without cookies. */
  @Test
  void widgetApiAllowsTheSiteAndFailsClosedWithoutG3() throws Exception {
    mockMvc
        .perform(get(WIDGET_API).param("url", ARTICLE).header("Origin", "http://localhost:3000"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }

  @Test
  void widgetApiRefusesOtherSites() throws Exception {
    mockMvc
        .perform(get(WIDGET_API).param("url", ARTICLE).header("Origin", "http://other.example"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  /** How many times V1 is recorded as applied: 1 when it ran, whatever migrations follow it. */
  private static long firstMigrationRuns(JdbcClient database) {
    return database
        .sql("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")
        .query(Long.class)
        .single();
  }

  private static Path widgetScript() {
    try {
      Path script = Files.createTempFile("rainmaker", ".js");
      script.toFile().deleteOnExit();
      return Files.writeString(script, "(function(){})();\n");
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create a stand-in widget script", e);
    }
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
