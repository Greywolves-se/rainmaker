package se.rainmaker.widgetdelivery.script;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the built rainmaker.js at {@code /widget/rainmaker.js} (contract, D-7), the address the
 * fake-news site loads it from.
 *
 * <p>For the demo it is read from {@code widget/dist/} after {@code npm run build}, so a new build
 * shows up without restarting G1. Later, S1 delivers the script through a CDN (architecture S1),
 * and this goes away.
 */
@RestController
@EnableConfigurationProperties(WidgetScriptProperties.class)
public class WidgetScriptController {

  private static final Logger LOG = LoggerFactory.getLogger(WidgetScriptController.class);

  private static final MediaType JAVASCRIPT =
      new MediaType("text", "javascript", StandardCharsets.UTF_8);

  private final Resource script;

  /** Creates the controller for the configured location. */
  public WidgetScriptController(WidgetScriptProperties properties, ResourceLoader resourceLoader) {
    this.script = resourceLoader.getResource(properties.scriptLocation());
    if (!script.isReadable()) {
      LOG.warn(
          "rainmaker.js not found at {}; it is served once npm run build in widget/ has made it",
          properties.scriptLocation());
    }
  }

  /**
   * The script, or 404 when it hasn't been built: the publisher's page then works without the panel
   * (fail open). {@code no-cache} makes browsers check for a newer build on every load.
   */
  @GetMapping("/widget/rainmaker.js")
  public ResponseEntity<Resource> script() {
    if (!script.isReadable()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok()
        .contentType(JAVASCRIPT)
        .cacheControl(CacheControl.noCache())
        .body(script);
  }
}
