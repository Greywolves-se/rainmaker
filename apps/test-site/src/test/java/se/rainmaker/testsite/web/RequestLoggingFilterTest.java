package se.rainmaker.testsite.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Trace ids: continue a valid W3C traceparent, ignore anything else. */
class RequestLoggingFilterTest {

  @Test
  void continuesValidTrace() {
    assertThat(
            RequestLoggingFilter.traceIdFrom(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
        .contains("4bf92f3577b34da6a3ce929d0e0e4736");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "garbage",
        "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
        "00-4bf92f35-00f067aa0ba902b7-01"
      })
  void ignoresInvalidHeaders(String header) {
    assertThat(RequestLoggingFilter.traceIdFrom(header)).isEmpty();
  }

  @Test
  void ignoresMissingHeader() {
    assertThat(RequestLoggingFilter.traceIdFrom(null)).isEmpty();
  }
}
