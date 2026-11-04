package se.rainmaker.contentintent.classification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * A cap on the Bedrock tokens (input and output) used per UTC day (#148). Once it is reached, no
 * more calls are made that day; the articles stay unclassified and are classified the next day.
 *
 * <p>The cap is checked before a call and charged after it, with the tokens the call really used.
 * So the last call of the day, or calls running at the same time, can go a little over the cap: it
 * is a brake, not exact accounting.
 *
 * <p>The count is kept in memory, so it starts again when the app restarts. That is fine as a brake
 * on runaway loops; the monthly budget and its cost stop cover the rest.
 */
final class DailyTokenBudget {

  private final long cap;
  private final Clock clock;
  private LocalDate day;
  private long used;

  DailyTokenBudget(long cap, Clock clock) {
    if (cap < 1) {
      throw new IllegalArgumentException("cap must be at least 1: " + cap);
    }
    this.cap = cap;
    this.clock = clock;
    this.day = today();
  }

  /** True while today's tokens are below the cap. */
  synchronized boolean hasRoom() {
    startNewDayIfNeeded();
    return used < cap;
  }

  /** Counts the tokens of one call; negative counts are ignored. */
  synchronized void spend(long tokens) {
    startNewDayIfNeeded();
    used += Math.max(0, tokens);
  }

  private void startNewDayIfNeeded() {
    LocalDate today = today();
    if (!today.equals(day)) {
      day = today;
      used = 0;
    }
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
