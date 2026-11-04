package se.rainmaker.matchingranking.matching;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import se.rainmaker.contentintent.api.Topic;
import se.rainmaker.matchingranking.offers.G3OffersClient;
import se.rainmaker.matchingranking.ranking.OfferForMatching;

/** One run of the retry (#248), with the store and G3 mocked; the G2 app tests use real ones. */
class MatchingRetryTest {

  private final G3OffersClient offers = mock(G3OffersClient.class);
  private final SuggestionStore store = mock(SuggestionStore.class);
  private final MatchingRetry retry =
      new MatchingRetry(offers, store, new MatchingRetrySettings(Duration.ofMinutes(5), 10, true));

  /** Review of #249: an error for one article must not hold up the rest of the run. */
  @Test
  void articleThatFailsDoesNotStopTheOthers() {
    WaitingVersion failing = waiting();
    WaitingVersion next = waiting();
    when(store.takeForRetry(anyInt(), any())).thenReturn(List.of(failing, next));
    when(offers.offers()).thenReturn(List.of(new OfferForMatching(UUID.randomUUID(), "home", 450)));
    when(store.save(eq(failing.articleId()), anyInt(), any()))
        .thenThrow(new DataAccessResourceFailureException("database hiccup"));

    retry.run();

    verify(store).save(eq(next.articleId()), eq(1), any());
  }

  private static WaitingVersion waiting() {
    return new WaitingVersion(UUID.randomUUID(), 1, List.of(Topic.HOME), "correlation");
  }
}
