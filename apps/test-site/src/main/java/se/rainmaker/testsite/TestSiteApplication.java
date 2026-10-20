package se.rainmaker.testsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts the Vintrabladet test site: a fictional Swedish news site used to develop and test
 * Rainmaker.
 *
 * <p>Packages:
 *
 * <ul>
 *   <li>{@code config} - settings from application.yml and environment variables
 *   <li>{@code content} - articles, front page choices and ads, loaded from {@code content/}
 *   <li>{@code view} - helpers that prepare data for the HTML templates
 *   <li>{@code web} - controllers (URLs), filters (headers, logging) and error pages
 *   <li>{@code feed} - RSS, news sitemap and the other machine-readable outputs
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TestSiteApplication {

  /** Starts the web server. */
  public static void main(String[] args) {
    SpringApplication.run(TestSiteApplication.class, args);
  }
}
