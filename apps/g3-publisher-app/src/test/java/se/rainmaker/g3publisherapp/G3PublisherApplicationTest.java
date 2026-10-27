package se.rainmaker.g3publisherapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import se.rainmaker.advertisermarketplace.database.AdvertiserMarketplaceDatabaseConfiguration;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;

/** The app starts against a real PostgreSQL set up exactly like the local one (#155). */
@SpringBootTest
class G3PublisherApplicationTest {

  @DynamicPropertySource
  static void databases(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
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
                    TestDatabases.url("content_ingestion"),
                    "publisher_control",
                    TestDatabases.LOCAL_PASSWORD))
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
}
