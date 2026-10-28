package se.rainmaker.matchingranking.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.rainmaker.contentintent.api.Topic;

/** The ranking rules (#149; contract section 4): fit by topic order, CPC only breaks ties. */
class OfferRankingTest {

  private static final OfferForMatching TRAVEL_600 = offer(1, "travel", 600);
  private static final OfferForMatching TRAVEL_450 = offer(2, "travel", 450);
  private static final OfferForMatching HOME_450 = offer(3, "home", 450);
  private static final OfferForMatching CARS_500 = offer(4, "cars", 500);

  @Test
  void greenTravelArticleGetsTheTravelOffers() {
    List<RankedOffer> ranked =
        OfferRanking.rank(
            List.of(Topic.TRAVEL), List.of(HOME_450, TRAVEL_450, CARS_500, TRAVEL_600));

    assertThat(ranked)
        .containsExactly(
            new RankedOffer(TRAVEL_600.offerId(), 1, Topic.TRAVEL),
            new RankedOffer(TRAVEL_450.offerId(), 2, Topic.TRAVEL));
  }

  @Test
  void offersThatFitEquallyWellAreOrderedByCpc() {
    List<RankedOffer> ranked =
        OfferRanking.rank(List.of(Topic.TRAVEL), List.of(TRAVEL_450, TRAVEL_600));

    assertThat(ranked)
        .extracting(RankedOffer::offerId)
        .containsExactly(TRAVEL_600.offerId(), TRAVEL_450.offerId());
  }

  @Test
  void betterFitAlwaysWinsOverHigherCpc() {
    OfferForMatching cheapHome = offer(5, "home", 100);
    OfferForMatching expensiveTravel = offer(6, "travel", 900);

    List<RankedOffer> ranked =
        OfferRanking.rank(List.of(Topic.HOME, Topic.TRAVEL), List.of(expensiveTravel, cheapHome));

    assertThat(ranked)
        .containsExactly(
            new RankedOffer(cheapHome.offerId(), 1, Topic.HOME),
            new RankedOffer(expensiveTravel.offerId(), 2, Topic.TRAVEL));
  }

  @Test
  void suggestsAtMostThreeOffers() {
    List<RankedOffer> ranked =
        OfferRanking.rank(
            List.of(Topic.TRAVEL, Topic.HOME, Topic.CARS),
            List.of(TRAVEL_600, TRAVEL_450, HOME_450, CARS_500));

    assertThat(ranked).extracting(RankedOffer::rank).containsExactly(1, 2, 3);
    assertThat(ranked)
        .extracting(RankedOffer::offerId)
        .containsExactly(TRAVEL_600.offerId(), TRAVEL_450.offerId(), HOME_450.offerId());
  }

  @Test
  void sameFitAndCpcAreOrderedByOfferIdSoTheResultNeverChanges() {
    OfferForMatching first = offer(7, "food", 300);
    OfferForMatching second = offer(8, "food", 300);

    assertThat(OfferRanking.rank(List.of(Topic.FOOD), List.of(second, first)))
        .extracting(RankedOffer::offerId)
        .containsExactly(first.offerId(), second.offerId());
  }

  /** Category keys live in G3 and in G2's Topic enum; one that drifts is skipped, not fatal. */
  @Test
  void skipsOfferWithUnknownCategory() {
    OfferForMatching pets = offer(9, "pets", 999);
    OfferForMatching noCategory = offer(10, null, 999);

    assertThat(OfferRanking.rank(List.of(Topic.TRAVEL), List.of(pets, noCategory, TRAVEL_450)))
        .extracting(RankedOffer::offerId)
        .containsExactly(TRAVEL_450.offerId());
  }

  @Test
  void otherNeverMatches() {
    OfferForMatching other = offer(11, "other", 999);

    assertThat(OfferRanking.rank(List.of(Topic.OTHER), List.of(other))).isEmpty();
  }

  @Test
  void noFittingOfferMeansNoSuggestions() {
    assertThat(OfferRanking.rank(List.of(Topic.CULTURE), List.of(TRAVEL_600, HOME_450))).isEmpty();
    assertThat(OfferRanking.rank(List.of(Topic.TRAVEL), List.of())).isEmpty();
  }

  private static OfferForMatching offer(int number, String category, int cpcOre) {
    return new OfferForMatching(
        UUID.fromString("01a1629f-c400-7000-8000-%012d".formatted(number)), category, cpcOre);
  }
}
