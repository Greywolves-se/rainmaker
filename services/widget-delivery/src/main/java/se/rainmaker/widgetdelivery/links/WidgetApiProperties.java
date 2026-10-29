package se.rainmaker.widgetdelivery.links;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The widget API's settings (#151, contract 7.4), under {@code
 * rainmaker.widget-delivery.widget-api}. Checked at start-up.
 *
 * @param allowedOrigins the publisher sites whose pages may call the API from the browser (CORS),
 *     each one origin such as {@code http://localhost:3000}: no path, no {@code *}
 */
@ConfigurationProperties(prefix = "rainmaker.widget-delivery.widget-api")
record WidgetApiProperties(List<String> allowedOrigins) {

  /** Checks the values. */
  WidgetApiProperties {
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      throw problem("allowed-origins must name at least one site");
    }
    allowedOrigins.forEach(WidgetApiProperties::requireOrigin);
    allowedOrigins = List.copyOf(allowedOrigins);
  }

  private static void requireOrigin(String origin) {
    URI uri;
    try {
      uri = new URI(origin);
    } catch (URISyntaxException notAddress) {
      throw problem("allowed-origins has " + origin + ", which is not an origin");
    }
    boolean http =
        "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    boolean onlyOrigin =
        uri.getHost() != null
            && uri.getRawUserInfo() == null
            && uri.getRawPath().isEmpty()
            && uri.getRawQuery() == null
            && uri.getRawFragment() == null;
    if (!http || !onlyOrigin) {
      throw problem(
          "allowed-origins has " + origin + "; each must be one http(s) origin, without a path");
    }
  }

  private static IllegalArgumentException problem(String problem) {
    return new IllegalArgumentException("rainmaker.widget-delivery.widget-api: " + problem);
  }
}
