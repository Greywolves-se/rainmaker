package se.rainmaker.trackingclick.click;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import se.rainmaker.trackingclick.database.TrackingClickDatabaseConfiguration;

/**
 * The stored clicks (#153, contract 3.6), append-only. The click id is the table's primary key, so
 * the same click is stored once however often, and however concurrently, it arrives.
 */
@Repository
class ClickStore {

  private final JdbcClient database;

  ClickStore(@Qualifier(TrackingClickDatabaseConfiguration.JDBC_CLIENT) JdbcClient database) {
    this.database = database;
  }

  /**
   * Stores the click unless its click id is already stored.
   *
   * @return true when this call stored it, false when it was already there
   */
  boolean record(ClickLink link) {
    // ON CONFLICT DO NOTHING: of two inserts with the same click id at the same time, PostgreSQL
    // lets one through and turns the other into "0 rows", never an error or a second row.
    int stored =
        database
            .sql(
                """
                INSERT INTO click (click_id, article_id, offer_id)
                VALUES (:clickId, :articleId, :offerId)
                ON CONFLICT (click_id) DO NOTHING
                """)
            .param("clickId", link.clickId())
            .param("articleId", link.articleId())
            .param("offerId", link.offerId())
            .update();
    return stored == 1;
  }
}
