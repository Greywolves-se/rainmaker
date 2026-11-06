package se.rainmaker.widgetdelivery.links;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * How the widget API answered, by result (#193): the counter {@code rainmaker.widget.links} with
 * one label, {@code result}, from a fixed list. Never an article URL or id as a label (CLAUDE.md,
 * "Logging and monitoring"). Every result is registered at start-up, so a dashboard shows 0 rather
 * than no data.
 */
@Component
class WidgetLinksCounters {

  /** The counter's name; Prometheus shows it as {@code rainmaker_widget_links_total}. */
  static final String NAME = "rainmaker.widget.links";

  /** How one request was answered. */
  enum Result {
    /** 200 with at least one link. */
    LINKS("links"),
    /** 200 with no links: the article has none, or isn't green. */
    EMPTY("empty"),
    /** 400: not an article on a publisher site (#224). */
    REFUSED("refused"),
    /** 503: G3 couldn't answer, or its circuit is open (fail closed). */
    UNAVAILABLE("unavailable");

    private final String label;

    Result(String label) {
      this.label = label;
    }
  }

  private final Map<Result, Counter> counters = new EnumMap<>(Result.class);

  WidgetLinksCounters(MeterRegistry meters) {
    for (Result result : Result.values()) {
      counters.put(
          result,
          Counter.builder(NAME)
              .description("Widget API answers, by result")
              .tag("result", result.label)
              .register(meters));
    }
  }

  void count(Result result) {
    counters.get(result).increment();
  }
}
