package se.rainmaker.contentintent.classification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * At most a set number of Bedrock calls in any rolling minute (#148). It never waits: a call over
 * the limit is refused, and the article stays unclassified for a later run. This stops a loop long
 * before AWS's cost data, which lags a few hours, would.
 */
final class CallRateLimiter {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final int callsPerMinute;
  private final Clock clock;
  private final Deque<Instant> recentCalls = new ArrayDeque<>();

  CallRateLimiter(int callsPerMinute, Clock clock) {
    if (callsPerMinute < 1) {
      throw new IllegalArgumentException("callsPerMinute must be at least 1: " + callsPerMinute);
    }
    this.callsPerMinute = callsPerMinute;
    this.clock = clock;
  }

  /** Takes one call from the allowance; false when the minute's calls are used. */
  synchronized boolean tryAcquire() {
    Instant now = clock.instant();
    Instant windowStart = now.minus(WINDOW);
    while (!recentCalls.isEmpty() && !recentCalls.peekFirst().isAfter(windowStart)) {
      recentCalls.pollFirst();
    }
    if (recentCalls.size() >= callsPerMinute) {
      return false;
    }
    recentCalls.addLast(now);
    return true;
  }
}
