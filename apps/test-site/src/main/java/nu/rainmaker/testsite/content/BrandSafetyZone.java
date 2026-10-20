package nu.rainmaker.testsite.content;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/**
 * Rainmaker's brand-safety zones (architecture UC-03): green = suitable for commercial links,
 * yellow = consider, red = never.
 */
public enum BrandSafetyZone {
  GREEN("green"),
  YELLOW("yellow"),
  RED("red");

  private final String id;

  BrandSafetyZone(String id) {
    this.id = id;
  }

  /** The id used in content files and the evaluation labels, e.g. "green". Also used in JSON. */
  @JsonValue
  public String id() {
    return id;
  }

  /** Finds a zone by its id. */
  public static Optional<BrandSafetyZone> fromId(String id) {
    return Arrays.stream(values()).filter(zone -> zone.id.equals(id)).findFirst();
  }
}
