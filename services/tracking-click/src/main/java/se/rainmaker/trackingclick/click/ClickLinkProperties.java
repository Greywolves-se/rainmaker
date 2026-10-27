package se.rainmaker.trackingclick.click;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the click links, under {@code rainmaker.tracking-click.link.*} (#153, contract 7.5).
 * Checked at start-up, so a wrong setting stops G1 instead of making links that don't work.
 *
 * @param baseUrl where G1 is reached from the reader's browser, e.g. {@code http://localhost:8081}
 * @param lifetime how long a link works after it was served (the contract: 24 hours)
 * @param signingKey the HMAC key, from {@code RAINMAKER_CLICK_SIGNING_KEY}; at least 32 characters.
 *     Empty on a developer's machine: then G1 makes a random key for each run
 */
@ConfigurationProperties(prefix = "rainmaker.tracking-click.link")
record ClickLinkProperties(String baseUrl, Duration lifetime, String signingKey) {

  /** The shortest key accepted: 32 characters, 256 bits for HMAC-SHA256 when they are random. */
  static final int MINIMUM_KEY_LENGTH = 32;

  /** Checks the values. */
  ClickLinkProperties {
    URI address = URI.create(baseUrl == null ? "" : baseUrl);
    if (!("http".equals(address.getScheme()) || "https".equals(address.getScheme()))
        || address.getHost() == null) {
      throw new IllegalArgumentException(
          "rainmaker.tracking-click.link.base-url must be an http(s) address: " + baseUrl);
    }
    if (lifetime == null || !lifetime.isPositive()) {
      throw new IllegalArgumentException("rainmaker.tracking-click.link.lifetime must be positive");
    }
    if (signingKey != null && !signingKey.isBlank() && signingKey.length() < MINIMUM_KEY_LENGTH) {
      // Never put the key itself in the message.
      throw new IllegalArgumentException(
          "RAINMAKER_CLICK_SIGNING_KEY must be at least " + MINIMUM_KEY_LENGTH + " characters");
    }
  }

  /** True when a key is configured. */
  boolean hasSigningKey() {
    return signingKey != null && !signingKey.isBlank();
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
        + "]";
  }
}
