package se.rainmaker.missioncontrol.navigatorapi;

import java.net.URI;
import java.time.Duration;

/**
 * How the Lambda calls OpenAI (ADR 0003). The model follows ADR 0002, so it is a setting, not code.
 *
 * @param baseUrl the API address, {@code https://api.openai.com} in production
 * @param model the model id from ADR 0002
 * @param maxOutputTokens upper limit on the answer's length
 * @param timeout how long one call may take; shorter than the Lambda's own timeout (about 30 s)
 * @param retryBackoff the wait before the single retry after a server error
 */
public record OpenAiSettings(
    URI baseUrl, String model, int maxOutputTokens, Duration timeout, Duration retryBackoff) {}
