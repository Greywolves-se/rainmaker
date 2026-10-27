package se.rainmaker.g3publisherapp;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.rainmaker.g3publisherapp.FakeG2Articles.HOME_OFFER;
import static se.rainmaker.g3publisherapp.FakeG2Articles.TRAVEL_OFFER;
import static se.rainmaker.g3publisherapp.FakeG2Articles.article;
import static se.rainmaker.g3publisherapp.FakeG2Articles.suggestion;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.rainmaker.publishercontrol.articles.ArticleSync;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;
import se.rainmaker.publishercontrol.decision.SuggestionDecisions;
import se.rainmaker.publishercontrol.decision.SuggestionNotDecidableException;
import se.rainmaker.publishercontrol.decision.SuggestionNotFoundException;

/**
 * The editor approves or rejects G2's suggestions, with an audit row each (#150, contract 5 and
 * 7.6). The articles come from a fake G2; the database is real (Testcontainers).
 */
@SpringBootTest(properties = "rainmaker.publisher-control.articles.polling=false")
class SuggestionDecisionsTest {

  private static final String EDITOR = "demo-editor";

  @RegisterExtension
  static WireMockExtension g2 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    registry.add("rainmaker.publisher-control.articles.base-url", g2::baseUrl);
  }

  @Autowired private ArticleSync sync;
  @Autowired private SuggestionDecisions decisions;

  @Autowired
  @Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient database;

  @Test
  void approvalAndRejectionEachWriteAnAuditRow() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    UUID travel = UUID.randomUUID();
    g2Sends(
        article(
            article,
            1,
            "green",
            suggestion(home, HOME_OFFER, 1),
            suggestion(travel, TRAVEL_OFFER, 2)));

    decisions.approve(home, EDITOR);
    decisions.reject(travel, EDITOR);

    assertThat(statuses(article)).isEqualTo(home + "=approved," + travel + "=rejected");
    assertThat(audit(home)).containsExactly("approved by demo-editor");
    assertThat(audit(travel)).containsExactly("rejected by demo-editor");
  }

  @Test
  void sameDecisionTwiceWritesOneRowAndChangingItWritesAnother() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));

    decisions.approve(home, EDITOR);
    decisions.approve(home, EDITOR);
    decisions.reject(home, EDITOR);

    assertThat(audit(home)).containsExactly("approved by demo-editor", "rejected by demo-editor");
  }

  @Test
  void suggestionOfArticleThatTurnedRedCannotBeApproved() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));
    decisions.approve(home, EDITOR);

    g2Sends(article(article, 1, "red"));

    assertThat(statuses(article)).isEqualTo(home + "=withdrawn");
    assertThatThrownBy(() -> decisions.approve(home, EDITOR))
        .isInstanceOf(SuggestionNotDecidableException.class);
    assertThat(audit(home)).containsExactly("approved by demo-editor");
  }

  /** D-5: the old approval doesn't cover the new text, so the editor approves again. */
  @Test
  void newVersionWithdrawsEvenApprovedSuggestions() {
    UUID article = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(first, HOME_OFFER, 1)));
    decisions.approve(first, EDITOR);

    g2Sends(article(article, 2, "green", suggestion(second, HOME_OFFER, 1)));

    assertThat(statuses(article)).isEqualTo(first + "=withdrawn," + second + "=suggested");
    assertThatThrownBy(() -> decisions.reject(first, EDITOR))
        .isInstanceOf(SuggestionNotDecidableException.class);
  }

  /** G2 may send the same article twice (contract 7.1); the editor's decision stays. */
  @Test
  void sameAnswerAgainKeepsTheDecision() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    String answer = article(article, 1, "green", suggestion(home, HOME_OFFER, 1));
    g2Sends(answer);
    decisions.approve(home, EDITOR);

    g2Sends(answer);

    assertThat(statuses(article)).isEqualTo(home + "=approved");
  }

  @Test
  void unknownSuggestionCannotBeDecided() {
    assertThatThrownBy(() -> decisions.reject(UUID.randomUUID(), EDITOR))
        .isInstanceOf(SuggestionNotFoundException.class);
  }

  /** Product rule: audit records are append-only, and the database refuses a change too. */
  @Test
  void auditRowsCannotBeChangedOrRemoved() {
    UUID article = UUID.randomUUID();
    UUID home = UUID.randomUUID();
    g2Sends(article(article, 1, "green", suggestion(home, HOME_OFFER, 1)));
    decisions.approve(home, EDITOR);

    assertThatThrownBy(
            () ->
                database
                    .sql("UPDATE suggestion_audit SET action = 'rejected' WHERE suggestion_id = ?")
                    .param(home)
                    .update())
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () ->
                database
                    .sql("DELETE FROM suggestion_audit WHERE suggestion_id = ?")
                    .param(home)
                    .update())
        .hasMessageContaining("append-only");
  }

  private void g2Sends(String... articles) {
    FakeG2Articles.g2Sends(g2, sync, articles);
  }

  private String statuses(UUID article) {
    return FakeG2Articles.statuses(database, article);
  }

  /** The suggestion's audit rows, oldest first: {@code action by editor}. */
  private List<String> audit(UUID suggestion) {
    return database
        .sql(
            "SELECT action, decided_by, decided_at FROM suggestion_audit WHERE suggestion_id = ?"
                + " ORDER BY decided_at, audit_id")
        .param(suggestion)
        .query(
            (row, number) -> {
              assertThat(row.getTimestamp("decided_at")).isNotNull();
              return row.getString("action") + " by " + row.getString("decided_by");
            })
        .list();
  }
}
