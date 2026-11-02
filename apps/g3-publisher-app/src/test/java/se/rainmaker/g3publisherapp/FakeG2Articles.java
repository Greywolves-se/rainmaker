package se.rainmaker.g3publisherapp;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import se.rainmaker.publishercontrol.articles.ArticleSync;

/**
 * Answers of G2's articles API (contract 7.1) for a fake G2, and a look at what G3 stored. Every
 * test uses its own article ids, because the database is shared.
 */
final class FakeG2Articles {

  static final String ARTICLES = "/api/articles";
  static final String PUBLISHED_AT = "\"publishedAt\": \"2026-10-10T05:45:00Z\"";
  static final UUID HOME_OFFER = UUID.fromString("01a1629f-c407-7538-b453-538453d70007");
  static final UUID TRAVEL_OFFER = UUID.fromString("01a1629f-c401-79e3-9779-9e3779b10001");

  private FakeG2Articles() {}

  /** Makes the fake G2's snapshot these articles, then lets G3 poll once. */
  static void g2Sends(WireMockExtension g2, ArticleSync sync, String... articles) {
    g2.stubFor(
        get(urlPathEqualTo(ARTICLES))
            .willReturn(okJson("{\"articles\": [" + String.join(",", articles) + "]}")));
    sync.pollOnce();
  }

  /** One classified article as G2 sends it, with every field. */
  static String article(UUID articleId, int version, String zone, String... suggestions) {
    String json =
        """
        {"articleId": "%s", "externalId": "vb-0006", "version": %d,
         "url": "http://localhost:3000/artikel/%s", "headline": "Badrummet som håller i 40 år",
         "section": "bostad", "language": "sv", %s,
         "updatedAt": null,
         "classification": {"status": "classified", "zone": "%s", "topics": ["home"],
           "reason": {"sv": "Praktiska råd.", "en": "Practical advice."},
           "model": "claude-haiku-5-5", "classifiedAt": "2026-10-30T09:15:04Z"},
         "suggestions": [%s],
         "correlationId": "c0a1"}
        """;
    return json.formatted(
        articleId, version, articleId, PUBLISHED_AT, zone, String.join(",", suggestions));
  }

  /**
   * An article G2 hasn't classified yet, or couldn't: status {@code unclassified} or {@code
   * failed}.
   */
  static String notClassified(UUID articleId, String status) {
    return article(articleId, 1, "green")
        .replace(
            "\"status\": \"classified\", \"zone\": \"green\"",
            "\"status\": \"" + status + "\", \"zone\": null");
  }

  /** One suggestion of an article's set. */
  static String suggestion(UUID suggestionId, UUID offerId, int rank) {
    String json =
        """
        {"suggestionId": "%s", "offerId": "%s", "rank": %d, "matchedTopics": ["home"]}
        """;
    return json.formatted(suggestionId, offerId, rank);
  }

  /** The article's stored suggestions, by version, then rank: {@code id=status,id=status}. */
  static String statuses(JdbcClient database, UUID article) {
    return database
        .sql(
            "SELECT suggestion_id, status FROM suggestion WHERE article_id = ?"
                + " ORDER BY article_version, rank")
        .param(article)
        .query((row, number) -> row.getString("suggestion_id") + "=" + row.getString("status"))
        .list()
        .stream()
        .collect(Collectors.joining(","));
  }
}
