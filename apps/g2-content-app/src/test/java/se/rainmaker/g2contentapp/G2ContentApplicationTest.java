package se.rainmaker.g2contentapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.rainmaker.contentingestion.database.ContentIngestionDatabaseConfiguration;
import se.rainmaker.contentintent.api.ArticleClassifier;
import se.rainmaker.contentintent.api.ArticleToClassify;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.Zone;
import se.rainmaker.contentintent.database.ContentIntentDatabaseConfiguration;
import se.rainmaker.matchingranking.database.MatchingRankingDatabaseConfiguration;

/** The app starts against a real PostgreSQL set up exactly like the local one (#155). */
@SpringBootTest
class G2ContentApplicationTest {

  @DynamicPropertySource
  static void databases(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
  }

  @Value("${server.port}")
  private int port;

  @Autowired
  @Qualifier(ContentIngestionDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient contentIngestion;

  @Autowired
  @Qualifier(ContentIntentDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient contentIntent;

  @Autowired
  @Qualifier(MatchingRankingDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient matchingRanking;

  @Autowired private ArticleClassifier classifier;

  /**
   * Without RAINMAKER_CLASSIFIER_MODE=bedrock, G2 uses the fake classifier. Its default must fail
   * closed, so a red article (a robbery, a fatal accident) never gets suggestions or a panel (#173
   * review, CLAUDE.md "Product rules").
   */
  @Test
  void defaultFakeClassificationNeverAllowsSuggestions() {
    Classification classification =
        classifier.classify(
            new ArticleToClassify(
                UUID.randomUUID(), 1, "Två döda i trafikolycka", "Två personer omkom."));

    assertThat(classification.zone()).isEqualTo(Zone.RED);
    assertThat(classification.mayGetSuggestions()).isFalse();
  }

  @Test
  void startsOnItsOwnPort() {
    assertThat(port).isEqualTo(8082);
  }

  @Test
  void everyModuleDatabaseHasItsFirstMigration() {
    assertThat(firstMigrationRuns(contentIngestion)).isEqualTo(1L);
    assertThat(firstMigrationRuns(contentIntent)).isEqualTo(1L);
    assertThat(firstMigrationRuns(matchingRanking)).isEqualTo(1L);
  }

  @Test
  void moduleLoginCannotOpenAnotherModulesDatabase() {
    assertThatThrownBy(
            () ->
                DriverManager.getConnection(
                    TestDatabases.url("tracking_click"),
                    "content_ingestion",
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
