package se.rainmaker.trackingclick.click;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * UUIDv7 ids (RFC 9562), as the contract asks for (section 3): the first 48 bits are the time in
 * Unix milliseconds, the rest is random, so ids sort by time. Java's {@code UUID.randomUUID()}
 * gives version 4, so this small generator is ours, with no new dependency.
 */
final class UuidV7 {

  private static final long VERSION_7 = 0x7L << 12;
  private static final int TWELVE_BITS = 1 << 12;
  private static final long VARIANT_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
  private static final long VARIANT_RFC_9562 = 0x8000_0000_0000_0000L;

  private final Clock clock;
  private final SecureRandom random;

  UuidV7(Clock clock, SecureRandom random) {
    this.clock = clock;
    this.random = random;
  }

  /** A new id for the current millisecond. */
  UUID next() {
    long mostSignificant = (clock.millis() << 16) | VERSION_7 | random.nextInt(TWELVE_BITS);
    long leastSignificant = (random.nextLong() & VARIANT_MASK) | VARIANT_RFC_9562;
    return new UUID(mostSignificant, leastSignificant);
  }
}
