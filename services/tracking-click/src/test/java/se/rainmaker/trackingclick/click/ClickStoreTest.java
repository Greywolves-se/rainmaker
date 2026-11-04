package se.rainmaker.trackingclick.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Clicks are stored once per click id, append-only, in a real PostgreSQL (#153). */
@Testcontainers
class ClickStoreTest {

  private static final int CONCURRENT_CLICKS = 8;

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  private static JdbcClient database;
  private static ClickStore clicks;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    // The same migrations as TrackingClickDatabaseConfiguration runs at start-up.
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/tracking-click")
        .load()
        .migrate();
    database = JdbcClient.create(dataSource);
    clicks = new ClickStore(database);
  }

  @Test
  void storesNewClick() {
    ClickLink link = newLink();

    assertThat(clicks.record(link)).isTrue();

    assertThat(rowsFor(link)).isEqualTo(1);
    assertThat(
            database
                .sql("SELECT article_id, offer_id FROM click WHERE click_id = :clickId")
                .param("clickId", link.clickId())
                .query((row, number) -> List.of(row.getObject(1), row.getObject(2)))
                .single())
        .containsExactly(link.articleId(), link.offerId());
  }

  @Test
  void storesSameClickTwiceAsOneRow() {
    ClickLink link = newLink();

    assertThat(clicks.record(link)).isTrue();
    assertThat(clicks.record(link)).isFalse();

    assertThat(rowsFor(link)).isEqualTo(1);
  }

  @Test
  void storesSameClickSentConcurrentlyAsOneRow() throws Exception {
    ClickLink link = newLink();
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Boolean>> sameClick = new ArrayList<>();
    for (int i = 0; i < CONCURRENT_CLICKS; i++) {
      sameClick.add(
          () -> {
            start.await();
            return clicks.record(link);
          });
    }

    List<Boolean> stored = new ArrayList<>();
    try (ExecutorService threads = Executors.newFixedThreadPool(CONCURRENT_CLICKS)) {
      List<Future<Boolean>> answers = new ArrayList<>();
      for (Callable<Boolean> click : sameClick) {
        answers.add(threads.submit(click));
      }
      start.countDown();
      for (Future<Boolean> answer : answers) {
        stored.add(answer.get());
      }
    }

    assertThat(stored).containsOnlyOnce(true);
    assertThat(rowsFor(link)).isEqualTo(1);
  }

  @Test
  void refusesToChangeStoredClick() {
    ClickLink link = newLink();
    clicks.record(link);

    assertThatThrownBy(
            () ->
                database
                    .sql("UPDATE click SET offer_id = :other WHERE click_id = :clickId")
                    .param("other", UUID.randomUUID())
                    .param("clickId", link.clickId())
                    .update())
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void refusesToDeleteStoredClick() {
    ClickLink link = newLink();
    clicks.record(link);

    assertThatThrownBy(
            () ->
                database
                    .sql("DELETE FROM click WHERE click_id = :clickId")
                    .param("clickId", link.clickId())
                    .update())
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThat(rowsFor(link)).isEqualTo(1);
  }

  /** A new click id per test, so the tests never see each other's rows (they can't be deleted). */
  private static ClickLink newLink() {
    return new ClickLink(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "http://localhost:8083/landing/1",
        Instant.parse("2026-10-31T10:00:00Z"));
  }

  private static long rowsFor(ClickLink link) {
    return database
        .sql("SELECT count(*) FROM click WHERE click_id = :clickId")
        .param("clickId", link.clickId())
        .query(Long.class)
        .single();
  }
}
