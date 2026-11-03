package se.rainmaker.trackingclick.click;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * How click links were opened, by result (#193): the counter {@code rainmaker.clicks} with one
 * label, {@code result}, from a fixed list. Never a click id, a token or a URL as a label
 * (CLAUDE.md, "Logging and monitoring"). Every result is registered at start-up, so a dashboard
 * shows 0 rather than no data.
 */
@Component
class ClickCounters {

  /** The counter's name; Prometheus shows it as {@code rainmaker_clicks_total}. */
  static final String NAME = "rainmaker.clicks";

  /** What happened to one opened link. */
  enum Result {
    /** Stored now, and redirected. */
    STORED("stored"),
    /** Its click id was already stored (a double-click or a retry), and redirected. */
    ALREADY_STORED("already_stored"),
    /** Forged, changed or garbled: 400. */
    INVALID("invalid"),
    /** Signed, but past its expiry: 410. */
    EXPIRED("expired"),
    /** Valid, but the database couldn't store it: 503, no redirect (#192). */
    NOT_STORED("not_stored"),
    /** A valid link asked with HEAD (a link checker or preview): 302, never stored (#192). */
    LOOKED_AT("looked_at");

    private final String label;

    Result(String label) {
      this.label = label;
    }
  }

  private final Map<Result, Counter> counters = new EnumMap<>(Result.class);

  ClickCounters(MeterRegistry meters) {
    for (Result result : Result.values()) {
      counters.put(
          result,
          Counter.builder(NAME)
              .description("Click links opened, by result")
              .tag("result", result.label)
              .register(meters));
    }
  }

  void count(Result result) {
    counters.get(result).increment();
  }
}
