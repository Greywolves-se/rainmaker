package se.rainmaker.contentingestion.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Article ids are UUIDv7, sorted by creation time (contract section 3; the same as #149). */
class UuidV7Test {

  private static final Instant AT = Instant.parse("2026-10-30T09:15:00Z");

  @Test
  void isVersionSevenWithTheStandardVariant() {
    UUID id = UuidV7.create(AT, new Random(1));

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void startsWithTheTimeInMilliseconds() {
    UUID id = UuidV7.create(AT, new Random(1));

    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(AT.toEpochMilli());
  }

  @Test
  void laterTimeSortsLater() {
    UUID earlier = UuidV7.create(AT, new Random(1));
    UUID later = UuidV7.create(AT.plusMillis(1), new Random(2));

    assertThat(later.toString()).isGreaterThan(earlier.toString());
  }

  @Test
  void differsWithinTheSameMillisecond() {
    Random random = new Random(3);

    assertThat(UuidV7.create(AT, random)).isNotEqualTo(UuidV7.create(AT, random));
  }
}
