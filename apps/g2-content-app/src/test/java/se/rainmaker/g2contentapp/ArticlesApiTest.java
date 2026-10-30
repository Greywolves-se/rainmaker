package se.rainmaker.g2contentapp;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import se.rainmaker.contentingestion.api.ArticleStored;
import se.rainmaker.contentingestion.database.ContentIngestionDatabaseConfiguration;
import se.rainmaker.contentintent.api.ArticleClassified;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import se.rainmaker.contentintent.database.ContentIntentDatabaseConfiguration;

/**
 * #202's acceptance criteria end to end in G2: an article stored by content-ingestion, classified
 * by content-intent (the fake classifier: green, topic "home") and matched against a fake G3 comes
 * out of {@code GET /api/articles} as contract 7.1 says. The database is real (Testcontainers);
 * feed polling is off. The rules for bad requests and the exact JSON are in {@code
 * ArticlesControllerTest}.
 */
@SpringBootTest(
    properties = {
      "rainmaker.content-ingestion.feed.polling=false",
      "rainmaker.content-intent.classifier.fake.zone=green"
    })
@AutoConfigureMockMvc
class ArticlesApiTest {

  private static final String API = "/api/articles";
  private static final Instant PUBLISHED = Instant.parse("2026-10-10T05:45:00Z");
  private static final Instant STORED = Instant.parse("2026-10-30T09:00:00Z");
  private static final String HOME_OFFER = "01a1629f-c407-7538-b453-538453d70007";

  @RegisterExtension
  static WireMockExtension g3 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    TestDatabases.register(registry);
    registry.add("rainmaker.matching-ranking.offers.base-url", g3::baseUrl);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEventPublisher events;

  @Autowired
  @Qualifier(ContentIngestionDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient contentIngestion;

  @Autowired
  @Qualifier(ContentIntentDatabaseConfiguration.JDBC_CLIENT)
  private JdbcClient contentIntent;

  @BeforeEach
  void g3HasHomeOffer() {
    g3.stubFor(
        WireMock.get(urlPathEqualTo("/api/offers"))
            .willReturn(
                okJson(
                    """
                    {"offers": [{"offerId": "%s", "category": "home", "cpcOre": 450}]}
                    """
                        .formatted(HOME_OFFER))));
  }

  @Test
  void storedClassifiedAndMatchedArticleIsInRecentAndUrl() throws Exception {
    ArticleStored article = storeAndAnnounce(UUID.randomUUID(), 1, STORED);

    for (ResultActions answer :
        List.of(recent(500), mockMvc.perform(get(API).param("url", article.url())))) {
      String found = "$.articles[?(@.articleId == '" + article.articleId() + "')]";
      answer
          .andExpect(status().isOk())
          .andExpect(jsonPath(found + ".version", contains(1)))
          .andExpect(jsonPath(found + ".headline", contains(article.headline())))
          .andExpect(jsonPath(found + ".classification.status", contains("classified")))
          .andExpect(jsonPath(found + ".classification.zone", contains("green")))
          .andExpect(jsonPath(found + ".classification.reason.en").isNotEmpty())
          .andExpect(jsonPath(found + ".suggestions[*].offerId", contains(HOME_OFFER)))
          .andExpect(jsonPath(found + ".correlationId", contains(article.correlationId())))
          .andExpect(jsonPath(found + ".text").doesNotExist());
    }
  }

  @Test
  void storedButNotYetClassifiedArticleIsUnclassifiedWithoutSuggestions() throws Exception {
    ArticleStored article = article(UUID.randomUUID(), 1);
    store(article, STORED);

    mockMvc
        .perform(get(API).param("url", article.url()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.articles[0].classification.status").value("unclassified"))
        .andExpect(jsonPath("$.articles[0].classification.zone").isEmpty())
        .andExpect(jsonPath("$.articles[0].suggestions").isEmpty());
  }

  /** As a retry would (#148): the same version, now red, stored and announced by content-intent. */
  @Test
  void reclassifiedArticleShowsNewZoneAndNoSuggestions() throws Exception {
    ArticleStored article = storeAndAnnounce(UUID.randomUUID(), 1, STORED);
    mockMvc
        .perform(get(API).param("url", article.url()))
        .andExpect(jsonPath("$.articles[0].suggestions.length()").value(1));

    contentIntent
        .sql(
            """
            UPDATE classification SET zone = 'red', topics = ARRAY['other']
            WHERE article_id = :articleId AND article_version = 1
            """)
        .param("articleId", article.articleId())
        .update();
    events.publishEvent(
        new ArticleClassified(
            article.articleId(),
            1,
            article.url(),
            article.headline(),
            Classification.classified(
                Zone.RED, List.of(Topic.OTHER), new Reason("Röd.", "Red."), "m", STORED),
            article.correlationId()));

    mockMvc
        .perform(get(API).param("url", article.url()))
        .andExpect(jsonPath("$.articles[0].classification.zone").value("red"))
        .andExpect(jsonPath("$.articles[0].suggestions").isEmpty());
  }

  /** Stored "in the future", so these two are the most recent of every test's articles. */
  @Test
  void editedArticleComesFirstInRecent() throws Exception {
    UUID edited = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    storeAndAnnounce(edited, 1, Instant.parse("2100-01-01T00:00:00Z"));
    storeAndAnnounce(other, 1, Instant.parse("2100-01-01T00:00:01Z"));
    storeAndAnnounce(edited, 2, Instant.parse("2100-01-01T00:00:02Z"));

    recent(2)
        .andExpect(
            jsonPath("$.articles[*].articleId")
                .value(contains(edited.toString(), other.toString())))
        .andExpect(jsonPath("$.articles[0].version").value(2))
        .andExpect(jsonPath("$.articles[0].suggestions[0].offerId").value(HOME_OFFER));
  }

  @Test
  void unknownUrlGivesEmptyList() throws Exception {
    mockMvc
        .perform(get(API).param("url", "http://localhost:3000/artikel/" + UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.articles").isEmpty());
  }

  private ResultActions recent(int count) throws Exception {
    return mockMvc.perform(get(API).param("recent", String.valueOf(count)));
  }

  /** Stores the version as content-ingestion does, then announces it, so G2 classifies it. */
  private ArticleStored storeAndAnnounce(UUID articleId, int version, Instant storedAt) {
    ArticleStored article = article(articleId, version);
    store(article, storedAt);
    events.publishEvent(article);
    return article;
  }

  private static ArticleStored article(UUID articleId, int version) {
    return new ArticleStored(
        articleId,
        version,
        "http://localhost:3000/artikel/" + articleId,
        "Badrummet som håller, version " + version,
        "Experterna ger sina bästa råd om badrumsrenovering.",
        "bostad",
        "sv",
        PUBLISHED,
        version == 1 ? null : PUBLISHED.plusSeconds(version),
        "correlation-" + articleId + "-" + version);
  }

  private void store(ArticleStored article, Instant storedAt) {
    contentIngestion
        .sql(
            """
            INSERT INTO article_version (article_id, version, url, headline, body_text, section,
                language, published_at, updated_at, last_modified, correlation_id, stored_at)
            VALUES (:articleId, :version, :url, :headline, :text, :section, :language,
                :publishedAt, :updatedAt, :publishedAt, :correlationId, :storedAt)
            """)
        .param("articleId", article.articleId())
        .param("version", article.version())
        .param("url", article.url())
        .param("headline", article.headline())
        .param("text", article.text())
        .param("section", article.section())
        .param("language", article.language())
        .param("publishedAt", article.publishedAt().atOffset(ZoneOffset.UTC))
        .param(
            "updatedAt",
            article.updatedAt() == null ? null : article.updatedAt().atOffset(ZoneOffset.UTC))
        .param("correlationId", article.correlationId())
        .param("storedAt", storedAt.atOffset(ZoneOffset.UTC))
        .update();
  }
}
