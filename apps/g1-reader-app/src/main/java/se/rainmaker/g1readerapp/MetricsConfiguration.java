package se.rainmaker.g1readerapp;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * G1's metric labels (#193; agreed on #193 for #188 too). Spring MVC times every request as {@code
 * http.server.requests}; this keeps only the labels {@code method}, {@code uri} (the route
 * template, e.g. {@code /c/{token}}, never the real address) and {@code outcome} (the status
 * class), so a label never holds an id, a token or a URL (CLAUDE.md, "Logging and monitoring").
 */
@Configuration(proxyBeanMethods = false)
class MetricsConfiguration {

  /** The labels {@code http.server.requests} keeps. */
  static final Set<String> REQUEST_LABELS = Set.of("method", "uri", "outcome");

  private static final String REQUESTS = "http.server.requests";

  @Bean
  MeterFilter requestsByRouteAndOutcomeOnly() {
    return new MeterFilter() {
      @Override
      public Meter.Id map(Meter.Id id) {
        if (!REQUESTS.equals(id.getName())) {
          return id;
        }
        return id.replaceTags(
            id.getTags().stream().filter(tag -> REQUEST_LABELS.contains(tag.getKey())).toList());
      }
    };
  }
}
