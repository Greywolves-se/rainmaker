package se.rainmaker.testsite.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes one structured log line per request, following the project's logging standard
 * (architecture 12.3-12.6).
 *
 * <p>For the duration of the request, {@code trace_id} and {@code correlation_id} are put in the
 * logging context (MDC), so every log line written while handling the request carries them. A valid
 * W3C {@code traceparent} header from the caller is continued instead of starting a new trace.
 *
 * <p>Privacy: only the path is logged - never the query string, IP address or other data that could
 * identify a reader.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  static final String TRACE_ID = "trace_id";
  static final String CORRELATION_ID = "correlation_id";

  private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

  /** W3C Trace Context header: version-traceid-parentid-flags. */
  private static final Pattern TRACEPARENT =
      Pattern.compile("^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long startedAt = System.nanoTime();
    MDC.put(TRACE_ID, traceIdFrom(request.getHeader("traceparent")).orElseGet(this::newTraceId));
    MDC.put(CORRELATION_ID, UUID.randomUUID().toString());
    try {
      chain.doFilter(request, response);
    } finally {
      LOG.atInfo()
          .addKeyValue("event.name", "http.request")
          .addKeyValue("http.request.method", request.getMethod())
          .addKeyValue("url.path", request.getRequestURI())
          .addKeyValue("http.response.status_code", response.getStatus())
          .addKeyValue("duration_ms", (System.nanoTime() - startedAt) / 1_000_000)
          .log("Request handled");
      MDC.remove(TRACE_ID);
      MDC.remove(CORRELATION_ID);
    }
  }

  /** The trace id from a valid traceparent header, if there is one. */
  static Optional<String> traceIdFrom(String traceparent) {
    if (traceparent == null) {
      return Optional.empty();
    }
    Matcher matcher = TRACEPARENT.matcher(traceparent);
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private String newTraceId() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
