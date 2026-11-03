package se.rainmaker.matchingranking.matching;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.matchingranking.api.CurrentSuggestions;
import se.rainmaker.matchingranking.api.Suggestion;
import se.rainmaker.matchingranking.database.MatchingRankingDatabaseConfiguration;
import se.rainmaker.matchingranking.ranking.RankedOffer;

/**
 * The current suggestion set per article, in matching's own database (#149).
 *
 * <ul>
 *   <li>An older article version than the newest one seen is ignored.
 *   <li>A newer version replaces the whole set (decision D-5), even with nothing.
 *   <li>The same version again replaces the set only if it differs, so the same event twice changes
 *       nothing and the suggestion ids stay the same.
 * </ul>
 *
 * <p>The article's row is locked while its set changes, so two events for one article are handled
 * one after the other.
 *
 * <p>A green version that couldn't be matched because G3 was down waits for offers (#248): its
 * topics are kept on the article's row until a retry matches it. Any later result for that version
 * or a newer one clears them.
 */
@Component
class SuggestionStore implements CurrentSuggestions {

  private final JdbcClient database;
  private final Clock clock = Clock.systemUTC();
  private final Random random = new SecureRandom();

  SuggestionStore(
      @Qualifier(MatchingRankingDatabaseConfiguration.JDBC_CLIENT) JdbcClient database) {
    this.database = database;
  }

  /**
   * Makes {@code ranked} the article's current set for this version.
   *
   * @return false if the version is older than the newest one seen, so nothing changed
   */
  @Transactional(MatchingRankingDatabaseConfiguration.TRANSACTION_MANAGER)
  public boolean save(UUID articleId, int version, List<RankedOffer> ranked) {
    int newest = lockNewestVersion(articleId, version);
    if (version < newest) {
      return false;
    }
    if (version > newest || !currentOffers(articleId).equals(ranked)) {
      replace(articleId, version, ranked);
    }
    stopWaiting(articleId);
    return true;
  }

  /**
   * Marks this green version as waiting for G3's offers (#248), unless a newer version has been
   * seen. A newer version also loses the old set: its text changed, so the set no longer covers it.
   *
   * @return false if the version is older than the newest one seen, so nothing changed
   */
  @Transactional(MatchingRankingDatabaseConfiguration.TRANSACTION_MANAGER)
  public boolean waitForOffers(
      UUID articleId, int version, List<Topic> topics, String correlationId) {
    int newest = lockNewestVersion(articleId, version);
    if (version < newest) {
      return false;
    }
    if (version > newest) {
      replace(articleId, version, List.of());
    }
    database
        .sql(
            """
            UPDATE matched_article
            SET waiting_topics = CAST(:topics AS text[]), waiting_correlation_id = :correlationId,
                attempted_at = now()
            WHERE article_id = :articleId
            """)
        .param("topics", topics.stream().map(Topic::key).toArray(String[]::new))
        .param("correlationId", correlationId)
        .param("articleId", articleId)
        .update();
    return true;
  }

  /**
   * Takes at most {@code limit} versions waiting for offers that haven't been tried for {@code
   * notTriedFor}, oldest attempt first, and marks them as tried now, so another run or another G2
   * doesn't take them too. Only the newest version of an article can be waiting.
   */
  List<WaitingVersion> takeForRetry(int limit, Duration notTriedFor) {
    return database
        .sql(
            """
            UPDATE matched_article SET attempted_at = now()
            WHERE article_id IN (
                SELECT article_id FROM matched_article
                WHERE waiting_topics IS NOT NULL
                    AND attempted_at < now() - make_interval(secs => :seconds)
                ORDER BY attempted_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED)
            RETURNING article_id, article_version, waiting_topics, waiting_correlation_id
            """)
        .param("seconds", notTriedFor.toSeconds())
        .param("limit", limit)
        .query(SuggestionStore::waitingVersion)
        .list();
  }

  @Override
  public List<Suggestion> forArticle(UUID articleId) {
    return database
        .sql(
            """
            SELECT suggestion_id, article_id, article_version, offer_id, rank, matched_topic
            FROM suggestion WHERE article_id = :articleId ORDER BY rank
            """)
        .param("articleId", articleId)
        .query(SuggestionStore::suggestion)
        .list();
  }

  @Override
  public Map<UUID, List<Suggestion>> forArticles(Collection<UUID> articleIds) {
    Map<UUID, List<Suggestion>> answer = new LinkedHashMap<>();
    articleIds.forEach(articleId -> answer.put(articleId, new ArrayList<>()));
    if (answer.isEmpty()) {
      return answer;
    }
    database
        .sql(
            """
            SELECT suggestion_id, article_id, article_version, offer_id, rank, matched_topic
            FROM suggestion WHERE article_id = ANY (CAST(:articleIds AS uuid[]))
            ORDER BY article_id, rank
            """)
        .param("articleIds", answer.keySet().stream().map(UUID::toString).toArray(String[]::new))
        .query(SuggestionStore::suggestion)
        .list()
        .forEach(suggestion -> answer.get(suggestion.articleId()).add(suggestion));
    answer.replaceAll((articleId, set) -> List.copyOf(set));
    return answer;
  }

  /** The newest version seen, after recording {@code version} if the article is new; locked. */
  private int lockNewestVersion(UUID articleId, int version) {
    database
        .sql(
            """
            INSERT INTO matched_article (article_id, article_version) VALUES (:articleId, :version)
            ON CONFLICT (article_id) DO NOTHING
            """)
        .param("articleId", articleId)
        .param("version", version)
        .update();
    return database
        .sql("SELECT article_version FROM matched_article WHERE article_id = :articleId FOR UPDATE")
        .param("articleId", articleId)
        .query(Integer.class)
        .single();
  }

  private void stopWaiting(UUID articleId) {
    database
        .sql(
            """
            UPDATE matched_article SET waiting_topics = NULL, waiting_correlation_id = NULL
            WHERE article_id = :articleId AND waiting_topics IS NOT NULL
            """)
        .param("articleId", articleId)
        .update();
  }

  private List<RankedOffer> currentOffers(UUID articleId) {
    return forArticle(articleId).stream()
        .map(
            suggestion ->
                new RankedOffer(
                    suggestion.offerId(),
                    suggestion.rank(),
                    Topic.fromKey(suggestion.matchedTopics().get(0)).orElseThrow()))
        .toList();
  }

  private void replace(UUID articleId, int version, List<RankedOffer> ranked) {
    database
        .sql("DELETE FROM suggestion WHERE article_id = :articleId")
        .param("articleId", articleId)
        .update();
    for (RankedOffer offer : ranked) {
      database
          .sql(
              """
              INSERT INTO suggestion
                (suggestion_id, article_id, article_version, offer_id, rank, matched_topic)
              VALUES (:suggestionId, :articleId, :version, :offerId, :rank, :topic)
              """)
          .param("suggestionId", UuidV7.create(clock.instant(), random))
          .param("articleId", articleId)
          .param("version", version)
          .param("offerId", offer.offerId())
          .param("rank", offer.rank())
          .param("topic", offer.matchedTopic().key())
          .update();
    }
    database
        .sql(
            """
            UPDATE matched_article SET article_version = :version, matched_at = now()
            WHERE article_id = :articleId
            """)
        .param("articleId", articleId)
        .param("version", version)
        .update();
  }

  private static WaitingVersion waitingVersion(ResultSet row, int rowNumber) throws SQLException {
    String[] keys = (String[]) row.getArray("waiting_topics").getArray();
    return new WaitingVersion(
        row.getObject("article_id", UUID.class),
        row.getInt("article_version"),
        Arrays.stream(keys).map(key -> Topic.fromKey(key).orElseThrow()).toList(),
        row.getString("waiting_correlation_id"));
  }

  private static Suggestion suggestion(ResultSet row, int rowNumber) throws SQLException {
    return new Suggestion(
        row.getObject("suggestion_id", UUID.class),
        row.getObject("article_id", UUID.class),
        row.getInt("article_version"),
        row.getObject("offer_id", UUID.class),
        row.getInt("rank"),
        List.of(row.getString("matched_topic")));
  }
}
