package se.rainmaker.publishercontrol.decision;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;

/**
 * The editor approves or rejects a suggestion (#150, contract 5 and 7.6). Each change writes an
 * audit row: who, when and what (product rule: every activation is audit-logged).
 *
 * <ul>
 *   <li>Only an open suggestion of the article's latest version can be decided, and only while the
 *       article is classified + green. A withdrawn one, or one of a red article, cannot (D-4, D-5).
 *   <li>The editor may change a decision; each change is a new audit row. The same decision again
 *       changes nothing, so it writes no row.
 * </ul>
 *
 * <p>The suggestion and its article are locked while deciding, so a new version from G2 can't
 * withdraw the suggestion halfway through.
 */
@Component
public class SuggestionDecisions {

  private final JdbcClient database;

  SuggestionDecisions(
      @Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT) JdbcClient database) {
    this.database = database;
  }

  /**
   * Approves the suggestion, so it may reach the widget.
   *
   * @throws SuggestionNotFoundException when there is no such suggestion
   * @throws SuggestionNotDecidableException when it is withdrawn or its article isn't green
   */
  @Transactional(PublisherControlDatabaseConfiguration.TRANSACTION_MANAGER)
  public void approve(UUID suggestionId, String editor) {
    decide(suggestionId, "approved", editor);
  }

  /**
   * Rejects the suggestion, so it never reaches the widget.
   *
   * @throws SuggestionNotFoundException when there is no such suggestion
   * @throws SuggestionNotDecidableException when it is withdrawn or its article isn't green
   */
  @Transactional(PublisherControlDatabaseConfiguration.TRANSACTION_MANAGER)
  public void reject(UUID suggestionId, String editor) {
    decide(suggestionId, "rejected", editor);
  }

  private void decide(UUID suggestionId, String decision, String editor) {
    String current =
        database
            .sql(
                """
                SELECT s.status
                FROM suggestion s JOIN article a ON a.article_id = s.article_id
                WHERE s.suggestion_id = :suggestionId
                  AND s.status <> 'withdrawn'
                  AND s.article_version = a.version
                  AND a.status = 'classified' AND a.zone = 'green'
                FOR UPDATE
                """)
            .param("suggestionId", suggestionId)
            .query(String.class)
            .optional()
            .orElseThrow(() -> notDecidable(suggestionId));
    if (current.equals(decision)) {
      return;
    }
    database
        .sql("UPDATE suggestion SET status = ? WHERE suggestion_id = ?")
        .params(decision, suggestionId)
        .update();
    database
        .sql("INSERT INTO suggestion_audit (suggestion_id, action, decided_by) VALUES (?, ?, ?)")
        .params(suggestionId, decision, editor)
        .update();
  }

  /** Tells an unknown suggestion apart from one that can't be decided (any more). */
  private RuntimeException notDecidable(UUID suggestionId) {
    boolean exists =
        database
                .sql("SELECT count(*) FROM suggestion WHERE suggestion_id = ?")
                .param(suggestionId)
                .query(Long.class)
                .single()
            > 0;
    return exists
        ? new SuggestionNotDecidableException(suggestionId)
        : new SuggestionNotFoundException(suggestionId);
  }
}
