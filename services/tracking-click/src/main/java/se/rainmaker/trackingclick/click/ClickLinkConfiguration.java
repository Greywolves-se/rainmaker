package se.rainmaker.trackingclick.click;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes S2's click links (#153). The signing key comes from the environment ({@code
 * RAINMAKER_CLICK_SIGNING_KEY}), never from code or a settings file, and G1 doesn't start without
 * it. Only the {@code local} profile allows a random key for each run: links served before a
 * restart then stop working, which is fine on a developer's machine. The key is never logged.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClickLinkProperties.class)
public class ClickLinkConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ClickLinkConfiguration.class);

  /** The click links, for S1's widget API ({@code ClickLinks}) and S2's redirect. */
  @Bean
  SignedClickLinks clickLinks(ClickLinkProperties settings) {
    SecureRandom random = new SecureRandom();
    Clock clock = Clock.systemUTC();
    return new SignedClickLinks(
        settings.baseUrl(),
        settings.lifetime(),
        signingKey(settings, random),
        clock,
        new UuidV7(clock, random));
  }

  private static byte[] signingKey(ClickLinkProperties settings, SecureRandom random) {
    if (settings.hasSigningKey()) {
      return settings.signingKey().getBytes(StandardCharsets.UTF_8);
    }
    LOG.warn(
        "RAINMAKER_CLICK_SIGNING_KEY is not set: click links are signed with a random key for this"
            + " run (local profile) and stop working when G1 restarts.");
    byte[] key = new byte[ClickLinkProperties.MINIMUM_KEY_LENGTH];
    random.nextBytes(key);
    return key;
  }
}
