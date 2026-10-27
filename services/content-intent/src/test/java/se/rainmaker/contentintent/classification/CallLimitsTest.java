package se.rainmaker.contentintent.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Our own brakes on Bedrock, which work before AWS's cost data catches up (#148): a maximum number
 * of calls per minute and a daily token cap.
 */
class CallLimitsTest {

  private static final Instant START = Instant.parse("2026-10-30T09:00:00Z");

  @Test
  void allowsAtMostTheConfiguredCallsInAnyMinute() {
    TestClock clock = new TestClock(START);
    CallRateLimiter limiter = new CallRateLimiter(2, clock);

    assertThat(limiter.tryAcquire()).isTrue();
    clock.advance(Duration.ofSeconds(30));
    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isFalse();

    // The first call leaves the one-minute window; the second is still in it.
    clock.advance(Duration.ofSeconds(31));
    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isFalse();
  }

  @Test
  void refusesNoCallsPerMinute() {
    assertThatThrownBy(() -> new CallRateLimiter(0, new TestClock(START)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stopsWhenTheDailyTokenCapIsUsedAndStartsAgainTheNextUtcDay() {
    TestClock clock = new TestClock(Instant.parse("2026-10-30T23:00:00Z"));
    DailyTokenBudget budget = new DailyTokenBudget(1_000, clock);

    assertThat(budget.hasRoom()).isTrue();
    budget.spend(600);
    assertThat(budget.hasRoom()).isTrue();
    budget.spend(400);
    assertThat(budget.hasRoom()).isFalse();

    clock.advance(Duration.ofHours(1));
    assertThat(budget.hasRoom()).isTrue();
  }

  @Test
  void ignoresNegativeTokenCounts() {
    DailyTokenBudget budget = new DailyTokenBudget(100, new TestClock(START));

    budget.spend(-500);
    budget.spend(100);

    assertThat(budget.hasRoom()).isFalse();
  }
}
