package se.rainmaker.missioncontrol.navigatorapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class KillSwitchTest {

  private static final Instant NOW = Instant.parse("2026-10-19T08:15:00Z");

  /** A clock the test moves forward by hand. */
  private static final class MovableClock extends Clock {
    private Instant now = NOW;

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  private final MovableClock clock = new MovableClock();

  @ParameterizedTest
  @ValueSource(strings = {"true", "TRUE", " true "})
  void isOnOnlyWhenTheValueIsTrue(String value) {
    assertThat(new KillSwitch(() -> value, clock).isOn()).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"false", "yes", "1", "tru"})
  void isOffForAnyOtherValue(String value) {
    assertThat(new KillSwitch(() -> value, clock).isOn()).isFalse();
  }

  @Test
  void isOffWhenTheValueCannotBeRead() {
    KillSwitch killSwitch =
        new KillSwitch(
            () -> {
              throw new IllegalStateException("parameter store unreachable");
            },
            clock);

    assertThat(killSwitch.isOn()).isFalse();
  }

  @Test
  void readsTheValueAtMostOncePerMinute() {
    AtomicInteger reads = new AtomicInteger();
    KillSwitch killSwitch =
        new KillSwitch(
            () -> {
              reads.incrementAndGet();
              return "true";
            },
            clock);

    killSwitch.isOn();
    clock.advance(Duration.ofSeconds(59));
    killSwitch.isOn();
    assertThat(reads).hasValue(1);

    clock.advance(Duration.ofSeconds(1));
    killSwitch.isOn();
    assertThat(reads).hasValue(2);
  }

  @Test
  void switchingOffTakesEffectWithinOneMinute() {
    AtomicReference<String> value = new AtomicReference<>("true");
    KillSwitch killSwitch = new KillSwitch(value::get, clock);
    assertThat(killSwitch.isOn()).isTrue();

    value.set("false");
    clock.advance(Duration.ofMinutes(1));

    assertThat(killSwitch.isOn()).isFalse();
  }

  @Test
  void keepsFailedReadForOneMinuteInsteadOfRetryingOnEveryQuestion() {
    AtomicInteger reads = new AtomicInteger();
    KillSwitch killSwitch =
        new KillSwitch(
            () -> {
              reads.incrementAndGet();
              throw new IllegalStateException("parameter store unreachable");
            },
            clock);

    killSwitch.isOn();
    killSwitch.isOn();

    assertThat(reads).hasValue(1);
  }
}
