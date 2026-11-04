package se.rainmaker.widgetdelivery.script;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the built rainmaker.js is, as a Spring resource location (contract, D-7).
 *
 * @param scriptLocation for example {@code file:widget/dist/rainmaker.js}, relative to the folder
 *     the app was started from
 */
@ConfigurationProperties("rainmaker.widget-delivery")
public record WidgetScriptProperties(String scriptLocation) {

  /** Fails at start-up, not on the first request, when the location is missing. */
  public WidgetScriptProperties {
    Objects.requireNonNull(scriptLocation, "rainmaker.widget-delivery.script-location must be set");
  }
}
