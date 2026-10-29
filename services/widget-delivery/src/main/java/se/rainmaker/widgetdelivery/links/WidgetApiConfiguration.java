package se.rainmaker.widgetdelivery.links;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the publisher's pages call the widget API from the browser (#151, contract 7.4): CORS for
 * the configured sites only, {@code GET} only and without cookies.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WidgetApiProperties.class)
class WidgetApiConfiguration implements WebMvcConfigurer {

  private final WidgetApiProperties properties;

  WidgetApiConfiguration(WidgetApiProperties properties) {
    this.properties = properties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping(WidgetLinksController.PATH)
        .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET")
        .allowCredentials(false);
  }
}
