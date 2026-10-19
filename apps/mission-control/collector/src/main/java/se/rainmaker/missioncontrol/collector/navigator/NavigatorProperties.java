package se.rainmaker.missioncontrol.collector.navigator;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the Navigator's AI step ({@code mission-control.collector.navigator.*}, #115, ADR
 * 0002).
 *
 * @param enabled false switches the step off; the snapshot then has no AI text
 * @param apiUrl OpenAI API address, e.g. {@code https://api.openai.com/v1}
 * @param apiKey OpenAI API key; may be empty, then the step reports an error
 * @param model the model, e.g. {@code gpt-6-luna}; change it here, no code change needed
 * @param maxOutputTokens the longest answer per page, in tokens (both languages)
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for an answer
 * @param maxAttempts attempts per page, including the first
 * @param backoff wait before the first retry; it doubles for each retry
 */
@ConfigurationProperties("mission-control.collector.navigator")
public record NavigatorProperties(
    boolean enabled,
    URI apiUrl,
    String apiKey,
    String model,
    int maxOutputTokens,
    Duration connectTimeout,
    Duration readTimeout,
    int maxAttempts,
    Duration backoff) {

  /** Checks the settings at startup. */
  public NavigatorProperties {
    Objects.requireNonNull(apiUrl, "mission-control.collector.navigator.api-url must be set");
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("mission-control.collector.navigator.model must be set");
    }
    Objects.requireNonNull(connectTimeout, "connect-timeout must be set");
    Objects.requireNonNull(readTimeout, "read-timeout must be set");
    Objects.requireNonNull(backoff, "backoff must be set");
    if (maxOutputTokens < 1 || maxAttempts < 1) {
      throw new IllegalArgumentException("max-output-tokens and max-attempts must be at least 1");
    }
  }

  /** Whether an API key is configured. */
  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }

  /** Like the generated one, but the key is never shown, so the settings are safe to log. */
  @Override
  public String toString() {
    return "NavigatorProperties[enabled="
        + enabled
        + ", apiUrl="
        + apiUrl
        + ", apiKey="
        + (hasApiKey() ? "***" : "(none)")
        + ", model="
        + model
        + ", maxOutputTokens="
        + maxOutputTokens
        + ", connectTimeout="
        + connectTimeout
        + ", readTimeout="
        + readTimeout
        + ", maxAttempts="
        + maxAttempts
        + ", backoff="
        + backoff
        + "]";
  }
}
