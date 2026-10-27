package se.rainmaker.trackingclick.click;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UUIDv7 as the contract asks for ids (section 3; RFC 9562). */
class UuidV7Test {

  private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

  private final TestClock clock = new TestClock(NOW);
  private final UuidV7 ids = new UuidV7(clock, new SecureRandom());

  @Test
  void isVersion7WithTheStandardVariant() {
    UUID id = ids.next();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void startsWithTheTimeInMilliseconds() {
    UUID id = ids.next();

    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
  }

  @Test
  void laterIdsSortAfterEarlierOnes() {
    UUID earlier = ids.next();
    clock.advance(Duration.ofMillis(1));
    UUID later = ids.next();

    assertThat(later.toString()).isGreaterThan(earlier.toString());
  }

  @Test
  void idsInTheSameMillisecondDiffer() {
    Set<UUID> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      seen.add(ids.next());
    }

    assertThat(seen).hasSize(1000);
  }
}
