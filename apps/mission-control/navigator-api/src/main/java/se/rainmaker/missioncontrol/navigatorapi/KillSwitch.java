package se.rainmaker.missioncontrol.navigatorapi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * The "Ask Navigator" kill switch (ADR 0003). Only the exact value {@code true} switches it on; any
 * other value, a missing value or a failed read counts as off, so a mistake can never cost money.
 * The value is read at most once a minute, so switching it off takes effect within a minute without
 * reading the parameter on every question.
 */
public final class KillSwitch {

  static final Duration CACHE_TIME = Duration.ofMinutes(1);

  /** Reads the switch's current raw value, e.g. an SSM parameter; may fail. */
  @FunctionalInterface
  public interface Reader {
    /** Returns the raw value, or {@code null} when it is not set; may throw when it fails. */
    String read();
  }

  private final Reader reader;
  private final Clock clock;
  private boolean on;
  private Instant readAt;

  /** Creates the switch on top of a reader, with the clock that decides when to read again. */
  public KillSwitch(Reader reader, Clock clock) {
    this.reader = Objects.requireNonNull(reader);
    this.clock = Objects.requireNonNull(clock);
  }

  /** Whether questions may be answered right now. */
  public synchronized boolean isOn() {
    Instant now = clock.instant();
    if (readAt == null || !now.isBefore(readAt.plus(CACHE_TIME))) {
      on = readSafely();
      readAt = now;
    }
    return on;
  }

  private boolean readSafely() {
    try {
      String value = reader.read();
      return value != null && value.strip().toLowerCase(Locale.ROOT).equals("true");
    } catch (RuntimeException readFailed) {
      // Fail closed for cost: an unreadable switch is off until the next read a minute later.
      return false;
    }
  }
}
