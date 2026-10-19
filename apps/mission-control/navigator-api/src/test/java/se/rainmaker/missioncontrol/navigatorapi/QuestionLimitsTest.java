package se.rainmaker.missioncontrol.navigatorapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import se.rainmaker.missioncontrol.navigatorapi.QuestionLimits.Result;

class QuestionLimitsTest {

  /** A fixed "now": Monday 19 Oct 2026, 10:15 in Stockholm. Never the wall clock. */
  private static final Instant NOW = Instant.parse("2026-10-19T08:15:00Z");

  private final InMemoryQuestionCounter counter = new InMemoryQuestionCounter();

  private QuestionLimits limitsAt(Instant instant) {
    return new QuestionLimits(counter, Clock.fixed(instant, ZoneOffset.UTC));
  }

  private static List<Result> ask(QuestionLimits limits, String user, int times) {
    List<Result> results = new ArrayList<>();
    for (int i = 0; i < times; i++) {
      results.add(limits.tryAsk(user));
    }
    return results;
  }

  @Test
  void allowsTenQuestionsPerPersonPerHourAndRefusesTheEleventh() {
    List<Result> results = ask(limitsAt(NOW), "user-a", 11);

    assertThat(results.subList(0, 10)).containsOnly(Result.ALLOWED);
    assertThat(results.get(10)).isEqualTo(Result.PERSON_LIMIT_REACHED);
  }

  @Test
  void allowsQuestionsAgainInTheNextHour() {
    ask(limitsAt(NOW), "user-a", 10);

    assertThat(limitsAt(Instant.parse("2026-10-19T09:00:00Z")).tryAsk("user-a"))
        .isEqualTo(Result.ALLOWED);
  }

  @Test
  void countsEachPersonOnTheirOwn() {
    ask(limitsAt(NOW), "user-a", 10);

    assertThat(limitsAt(NOW).tryAsk("user-b")).isEqualTo(Result.ALLOWED);
  }

  @Test
  void allowsTwoHundredQuestionsPerDayForTheWholeTeam() {
    QuestionLimits limits = limitsAt(NOW);
    for (int user = 0; user < 20; user++) {
      assertThat(ask(limits, "user-" + user, 10)).containsOnly(Result.ALLOWED);
    }

    assertThat(limits.tryAsk("user-new")).isEqualTo(Result.TEAM_LIMIT_REACHED);
  }

  @Test
  void startsTheTeamDayAtMidnightStockholmTime() {
    QuestionLimits limits = limitsAt(NOW);
    for (int user = 0; user < 20; user++) {
      ask(limits, "user-" + user, 10);
    }

    // 22:30 UTC on the 19th is 00:30 on the 20th in Stockholm (UTC+2), a new team day.
    assertThat(limitsAt(Instant.parse("2026-10-19T22:30:00Z")).tryAsk("user-new"))
        .isEqualTo(Result.ALLOWED);
  }

  @Test
  void refusedQuestionDoesNotUseTheTeamQuota() {
    QuestionLimits limits = limitsAt(NOW);
    ask(limits, "user-a", 15);

    assertThat(counter.count("team#2026-10-19")).isEqualTo(10);
  }

  @Test
  void keysThePersonByLoginIdAndHourAndExpiresAtTheEndOfTheWindow() {
    List<CounterLimit> seen = new ArrayList<>();
    QuestionCounter recording =
        limits -> {
          seen.addAll(limits);
          return Optional.empty();
        };

    new QuestionLimits(recording, Clock.fixed(NOW, ZoneOffset.UTC)).tryAsk("sub-123");

    assertThat(seen)
        .containsExactly(
            new CounterLimit(
                "person#sub-123#2026-10-19T08:00:00Z", 10, Instant.parse("2026-10-19T09:00:00Z")),
            new CounterLimit("team#2026-10-19", 200, Instant.parse("2026-10-19T22:00:00Z")));
  }
}
