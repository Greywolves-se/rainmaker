package nu.rainmaker.testsite.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import nu.rainmaker.testsite.config.SiteProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds security headers to every response.
 *
 * <p>The Content-Security-Policy is strict, like many real publishers': only the site's own files
 * may load - plus the Rainmaker widget's origin when one is configured. That lets us find out early
 * which domains the widget needs allow-listed (architecture P5). There are no inline scripts or
 * styles anywhere on the site, so none are allowed.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

  private final String contentSecurityPolicy;

  /** Creates the filter; the policy is built once from the configuration. */
  public SecurityHeadersFilter(SiteProperties siteProperties) {
    String widgetOrigin =
        siteProperties.rainmaker().origin().map(origin -> " " + origin).orElse("");
    this.contentSecurityPolicy =
        String.join(
            "; ",
            "default-src 'self'",
            "script-src 'self'" + widgetOrigin,
            "connect-src 'self'" + widgetOrigin,
            "img-src 'self' data:",
            "style-src 'self'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Content-Security-Policy", contentSecurityPolicy);
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    // Fictional news must never show up in real search results.
    response.setHeader("X-Robots-Tag", "noindex");
    chain.doFilter(request, response);
  }
}
