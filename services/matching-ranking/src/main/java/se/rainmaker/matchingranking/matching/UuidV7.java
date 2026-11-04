package se.rainmaker.matchingranking.matching;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562): 48 bits of Unix time in milliseconds, then random bits, so ids sort by
 * creation time (#145, section 3). Java only makes version 4, and the team chose a small generator
 * over a new dependency.
 */
final class UuidV7 {

  private static final long VERSION_7 = 0x7000L;
  private static final long RANDOM_12_BITS = 0x0FFFL;
  private static final long VARIANT_BITS = 0x8000_0000_0000_0000L;
  private static final long RANDOM_62_BITS = 0x3FFF_FFFF_FFFF_FFFFL;

  private UuidV7() {}

  /** A new id for this time, with random bits from {@code random}. */
  static UUID create(Instant time, Random random) {
    long mostSignificant =
        (time.toEpochMilli() << 16) | VERSION_7 | (random.nextLong() & RANDOM_12_BITS);
    long leastSignificant = VARIANT_BITS | (random.nextLong() & RANDOM_62_BITS);
    return new UUID(mostSignificant, leastSignificant);
  }
}
