package se.rainmaker.g2contentapp.articles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import se.rainmaker.contentingestion.api.LatestArticles;
import se.rainmaker.contentingestion.api.StoredArticle;
import se.rainmaker.contentintent.api.ArticleVersion;
import se.rainmaker.contentintent.api.Classification;
import se.rainmaker.contentintent.api.CurrentClassifications;
import se.rainmaker.contentintent.api.Reason;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.contentintent.api.Zone;
import se.rainmaker.matchingranking.api.CurrentSuggestions;
import se.rainmaker.matchingranking.api.Suggestion;

/**
 * The articles API's rules (#202, contract 7.1), with the three modules mocked: the answer exactly
 * as the contract shows it, suggestions only for a green version's own set, and 400 for anything
 * but exactly one of {@code recent} (1–500) and {@code url}. {@code ArticlesApiTest} runs it
 * against the real modules.
 */
class ArticlesControllerTest {

  private static final String API = "/api/articles";

  /** The article in contract example 7.1. */
  private static final String EXAMPLE = ContractExamples.read("7.1-articles.json");

  private static final UUID ARTICLE_ID = UUID.fromString("0192f3c2-5e1a-7b10-8a3c-4d5e6f708192");
  private static final String URL =
      "http://localhost:3000/artikel/badrumsrenovering-experternas-rad";
  private static final StoredArticle ARTICLE =
      new StoredArticle(
          ARTICLE_ID,
          "vb-0006",
          1,
          URL,
          "Badrummet som håller i 40 år – experternas bästa råd",
          "bostad",
          "sv",
          Instant.parse("2026-10-10T05:45:00Z"),
          null,
          "0192f3c2-5e1a-7b10-8a3c-c0a1c0a1c0a1");
  private static final ArticleVersion VERSION = new ArticleVersion(ARTICLE_ID, 1);
  private static final Classification GREEN_HOME =
      Classification.classified(
          Zone.GREEN,
          List.of(Topic.HOME),
          new Reason(
              "Praktiska råd om badrumsrenovering; tydlig köpavsikt, inget känsligt.",
              "Practical advice on renovating a bathroom; clear buying intent, nothing"
                  + " sensitive."),
          "claude-haiku-5-5",
          Instant.parse("2026-10-30T09:15:04Z"));
  private static final Suggestion SUGGESTION =
      new Suggestion(
          UUID.fromString("0192f3e4-7a1b-7c20-9d3e-0f1a2b3c4d5e"),
          ARTICLE_ID,
          1,
          UUID.fromString("0192f3d0-2b4c-7d10-9e8f-a1b2c3d4e5f6"),
          1,
          List.of("home"));

  private final LatestArticles latest = mock(LatestArticles.class);
  private final CurrentClassifications classifications = mock(CurrentClassifications.class);
  private final CurrentSuggestions suggestions = mock(CurrentSuggestions.class);

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new ArticlesController(new CurrentArticles(latest, classifications, suggestions)))
          .build();

  private void stored(StoredArticle article, Classification classification, Suggestion... set) {
    when(latest.recent(anyInt())).thenReturn(List.of(article));
    when(latest.byUrl(article.url())).thenReturn(Optional.of(article));
    when(classifications.forVersions(List.of(new ArticleVersion(article.articleId(), 1))))
        .thenReturn(Map.of(new ArticleVersion(article.articleId(), 1), classification));
    when(suggestions.forArticles(List.of(article.articleId())))
        .thenReturn(Map.of(article.articleId(), List.of(set)));
  }

  @Test
  void answersRecentExactlyAsTheContractShowsIt() throws Exception {
    stored(ARTICLE, GREEN_HOME, SUGGESTION);

    mockMvc
        .perform(get(API).param("recent", "100"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json(EXAMPLE, JsonCompareMode.STRICT))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void answersUrlExactlyAsTheContractShowsIt() throws Exception {
    stored(ARTICLE, GREEN_HOME, SUGGESTION);

    mockMvc
        .perform(get(API).param("url", URL))
        .andExpect(status().isOk())
        .andExpect(content().json(EXAMPLE, JsonCompareMode.STRICT));
  }

  @Test
  void unknownUrlGivesEmptyList() throws Exception {
    when(latest.byUrl(anyString())).thenReturn(Optional.empty());
    when(classifications.forVersions(List.of())).thenReturn(Map.of());
    when(suggestions.forArticles(List.of())).thenReturn(Map.of());

    mockMvc
        .perform(get(API).param("url", URL + "/okand"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"articles\": []}", JsonCompareMode.STRICT));
  }

  @Test
  void unclassifiedArticleHasNoZoneAndNoSuggestions() throws Exception {
    stored(ARTICLE, Classification.unclassified(), SUGGESTION);

    mockMvc
        .perform(get(API).param("recent", "1"))
        .andExpect(jsonPath("$.articles[0].classification.status").value("unclassified"))
        .andExpect(jsonPath("$.articles[0].classification.zone").isEmpty())
        .andExpect(jsonPath("$.articles[0].classification.topics").isEmpty())
        .andExpect(jsonPath("$.articles[0].suggestions").isEmpty());
  }

  /** Matching may not have caught up with a reclassification yet: its old set is never shown. */
  @Test
  void redArticleNeverHasSuggestions() throws Exception {
    stored(
        ARTICLE,
        Classification.classified(
            Zone.RED, List.of(Topic.OTHER), new Reason("Olycka.", "Accident."), "m", Instant.EPOCH),
        SUGGESTION);

    mockMvc
        .perform(get(API).param("recent", "1"))
        .andExpect(jsonPath("$.articles[0].classification.zone").value("red"))
        .andExpect(jsonPath("$.articles[0].suggestions").isEmpty());
  }

  /** Matching may still hold the set of an older version (D-5): it isn't this version's. */
  @Test
  void suggestionsOfOlderVersionAreNotShown() throws Exception {
    StoredArticle edited =
        new StoredArticle(
            ARTICLE_ID,
            "vb-0006",
            2,
            URL,
            "Ny rubrik",
            "bostad",
            "sv",
            Instant.EPOCH,
            Instant.EPOCH,
            "correlation-2");
    when(latest.recent(anyInt())).thenReturn(List.of(edited));
    when(classifications.forVersions(anyCollection()))
        .thenReturn(Map.of(new ArticleVersion(ARTICLE_ID, 2), GREEN_HOME));
    when(suggestions.forArticles(anyCollection()))
        .thenReturn(Map.of(ARTICLE_ID, List.of(SUGGESTION)));

    mockMvc
        .perform(get(API).param("recent", "1"))
        .andExpect(jsonPath("$.articles[0].version").value(2))
        .andExpect(jsonPath("$.articles[0].suggestions").isEmpty());
  }

  @Test
  void putsCallersCorrelationIdOnLogLines() throws Exception {
    AtomicReference<String> duringLookup = new AtomicReference<>();
    when(latest.byUrl(anyString()))
        .thenAnswer(
            call -> {
              duringLookup.set(MDC.get("correlation_id"));
              return Optional.empty();
            });
    when(classifications.forVersions(anyCollection())).thenReturn(Map.of());
    when(suggestions.forArticles(anyCollection())).thenReturn(Map.of());

    mockMvc
        .perform(get(API).param("url", URL).header("X-Correlation-Id", "c0a1-c0a1"))
        .andExpect(status().isOk());

    assertThat(duringLookup).hasValue("c0a1-c0a1");
    assertThat(MDC.get("correlation_id")).isNull();
  }

  @Test
  void refusesRequestWithNeitherRecentNorUrl() throws Exception {
    mockMvc.perform(get(API)).andExpect(status().isBadRequest());

    verifyNoInteractions(latest);
  }

  @Test
  void refusesRequestWithBothRecentAndUrl() throws Exception {
    mockMvc
        .perform(get(API).param("recent", "100").param("url", URL))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(latest);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "501", "1000", "99999999999", "-1", "+5", "1.5", "abc", "", " "})
  void refusesRecentThatIsNotWholeNumberFromOneTo500(String recent) throws Exception {
    mockMvc.perform(get(API).param("recent", recent)).andExpect(status().isBadRequest());

    verifyNoInteractions(latest);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void refusesBlankUrl(String url) throws Exception {
    mockMvc.perform(get(API).param("url", url)).andExpect(status().isBadRequest());

    verifyNoInteractions(latest);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "500"})
  void acceptsRecentAtTheLimits(String recent) throws Exception {
    stored(ARTICLE, GREEN_HOME);

    mockMvc.perform(get(API).param("recent", recent)).andExpect(status().isOk());
  }
}
