package se.rainmaker.missioncontrol.navigatorapi;

import java.time.Instant;

/**
 * One counter a question is counted against.
 *
 * @param id the counter's key, e.g. {@code person#<login id>#<hour>} or {@code team#<day>}
 * @param limit how many questions the counter allows
 * @param expiresAt when the counter's window ends; the store may delete it after that
 */
public record CounterLimit(String id, int limit, Instant expiresAt) {}
