package se.rainmaker.trackingclick.click;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the click links, under {@code rainmaker.tracking-click.link.*} (#153, contract 7.5).
 * Checked at start-up, so a wrong or missing setting stops G1 instead of making links that don't
 * work. Only the {@code local} profile (a developer's machine) sets an address and allows running
 * without a key.
 *
 * @param baseUrl where G1 is reached from the reader's browser, e.g. {@code http://localhost:8081};
 *     no default outside the {@code local} profile
 * @param lifetime how long a link works after it was served (the contract: 24 hours)
 * @param signingKey the HMAC key, from {@code RAINMAKER_CLICK_SIGNING_KEY}, trimmed; at least 32
 *     characters. Required unless {@code allowRandomSigningKey}
 * @param allowRandomSigningKey {@code true} only in the {@code local} profile: without a key, G1
 *     signs with a random key for each run. Anywhere else that would lose clicks: links stop
 *     working after a restart and on any other G1
 */
@ConfigurationProperties(prefix = "rainmaker.tracking-click.link")
record ClickLinkProperties(
    String baseUrl,
    Duration lifetime,
    String signingKey,
    @DefaultValue("false") boolean allowRandomSigningKey) {

  /** The shortest key accepted: 32 characters, 256 bits for HMAC-SHA256 when they are random. */
  static final int MINIMUM_KEY_LENGTH = 32;

  private static final String KEY_VARIABLE = "RAINMAKER_CLICK_SIGNING_KEY";

  /** Trims the key and checks the values. Never puts the key itself in a message. */
  ClickLinkProperties {
    HttpAddresses.requireAbsoluteHttp(
        baseUrl,
        "rainmaker.tracking-click.link.base-url (RAINMAKER_CLICK_BASE_URL, or on a developer's"
            + " machine the local profile)");
    if (lifetime == null || !lifetime.isPositive()) {
      throw new IllegalArgumentException("rainmaker.tracking-click.link.lifetime must be positive");
    }
    String trimmed = signingKey == null ? "" : signingKey.strip();
    if (trimmed.isEmpty() && signingKey != null && !signingKey.isEmpty()) {
      throw new IllegalArgumentException(KEY_VARIABLE + " is only spaces");
    }
    if (trimmed.isEmpty() && !allowRandomSigningKey) {
      throw new IllegalArgumentException(
          KEY_VARIABLE
              + " is not set. Set it, or on a developer's machine run G1 with the local profile"
              + " (--spring.profiles.active=local)");
    }
    if (!trimmed.isEmpty() && trimmed.length() < MINIMUM_KEY_LENGTH) {
      throw new IllegalArgumentException(
          KEY_VARIABLE + " must be at least " + MINIMUM_KEY_LENGTH + " characters");
    }
    signingKey = trimmed.isEmpty() ? null : trimmed;
  }

  /** True when a key is configured. */
  boolean hasSigningKey() {
    return signingKey != null;
  }

  /** Never the key itself: a record's own {@code toString} would print it in any log of this. */
  @Override
  public String toString() {
    return "ClickLinkProperties[baseUrl="
        + baseUrl
        + ", lifetime="
        + lifetime
        + ", signingKey="
        + (hasSigningKey() ? "<set>" : "<not set>")
        + ", allowRandomSigningKey="
        + allowRandomSigningKey
        + "]";
  }
}
