package se.rainmaker.missioncontrol.navigatorapi;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * The limits from ADR 0003: at most 10 questions per person per hour and 200 per day for the whole
 * team. A person is identified by the login's verified {@code sub} id, never an e-mail address.
 * Hours and days follow Stockholm time, like the rest of the dashboard.
 */
public final class QuestionLimits {

  static final int PER_PERSON_PER_HOUR = 10;
  static final int TEAM_PER_DAY = 200;

  private static final ZoneId STOCKHOLM = ZoneId.of("Europe/Stockholm");

  /** Whether a question may be asked, and which limit stopped it if not. */
  public enum Result {
    ALLOWED,
    PERSON_LIMIT_REACHED,
    TEAM_LIMIT_REACHED
  }

  private final QuestionCounter counter;
  private final Clock clock;

  /** Creates the limits on top of a counter store, with the clock that decides the windows. */
  public QuestionLimits(QuestionCounter counter, Clock clock) {
    this.counter = Objects.requireNonNull(counter);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Counts one question for a person if neither limit is reached; a refused question is not counted
   * against either limit.
   *
   * @param loginId the verified login {@code sub} id of the person asking
   */
  public Result tryAsk(String loginId) {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("A question needs a verified login id");
    }
    ZonedDateTime now = clock.instant().atZone(STOCKHOLM);
    ZonedDateTime hour = now.truncatedTo(ChronoUnit.HOURS);
    LocalDate day = now.toLocalDate();
    CounterLimit person =
        new CounterLimit(
            "person#" + loginId + "#" + hour.toInstant(),
            PER_PERSON_PER_HOUR,
            hour.plusHours(1).toInstant());
    CounterLimit team =
        new CounterLimit(
            "team#" + day, TEAM_PER_DAY, day.plusDays(1).atStartOfDay(STOCKHOLM).toInstant());

    return counter
        .tryIncrement(List.of(person, team))
        .map(
            full ->
                full.equals(person.id()) ? Result.PERSON_LIMIT_REACHED : Result.TEAM_LIMIT_REACHED)
        .orElse(Result.ALLOWED);
  }
}
