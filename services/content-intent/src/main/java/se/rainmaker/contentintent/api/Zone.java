package se.rainmaker.contentintent.api;

import java.util.Arrays;
import java.util.Optional;

/** Brand-safety zone of a classified article (#145, section 3.3). */
public enum Zone {
  /** Safe, with commercial intent: may get suggestions. */
  GREEN("green"),
  /** Sensitive (health, personal finance): no suggestions in the demo. */
  YELLOW("yellow"),
  /** Never next to ads (crime, tragedy): never suggestions, never a panel. */
  RED("red");

  private final String key;

  Zone(String key) {
    this.key = key;
  }

  /** The value used in JSON and the database, e.g. {@code "green"}. */
  public String key() {
    return key;
  }

  /** The zone with this exact key, if any. */
  public static Optional<Zone> fromKey(String key) {
    return Arrays.stream(values()).filter(zone -> zone.key.equals(key)).findFirst();
  }
}
