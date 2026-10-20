package nu.rainmaker.testsite.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Site settings from the {@code site:} block in application.yml.
 *
 * <p>The values are checked when the application starts, so a wrong setting stops the site with a
 * clear message instead of causing strange behaviour later.
 *
 * @param baseUrl public address of the site, without a trailing slash
 * @param contentDirectory folder with {@code articles/}, {@code front-page.json} and {@code
 *     ads.json}
 * @param rainmaker how the Rainmaker widget is added to the pages
 */
@ConfigurationProperties(prefix = "site")
public record SiteProperties(String baseUrl, Path contentDirectory, Rainmaker rainmaker) {

  /** Checks and normalises the values. */
  public SiteProperties {
    baseUrl = requireHttpUrl(baseUrl, "site.base-url").replaceAll("/+$", "");
    if (contentDirectory == null) {
      throw new IllegalArgumentException("site.content-directory must be set");
    }
    if (rainmaker == null) {
      rainmaker = new Rainmaker(null, InstallMethod.TEMPLATE);
    }
  }

  /** Returns an absolute URL for a path on this site, e.g. "/rss.xml". */
  public String absoluteUrl(String path) {
    return baseUrl + path;
  }

  /**
   * How the Rainmaker widget script is added.
   *
   * @param scriptUrl address of rainmaker.js, or empty for no widget
   * @param installMethod where the script tag comes from
   */
  public record Rainmaker(String scriptUrl, InstallMethod installMethod) {

    /** Checks the widget address: https only, plain http only for localhost. */
    public Rainmaker {
      if (scriptUrl != null && scriptUrl.isBlank()) {
        scriptUrl = null;
      }
      if (scriptUrl != null) {
        URI uri = parse(scriptUrl, "site.rainmaker.script-url");
        boolean isLocal = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        boolean allowed =
            "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && isLocal);
        if (!allowed) {
          throw new IllegalArgumentException(
              "site.rainmaker.script-url must use https (http only for localhost): " + scriptUrl);
        }
      }
      if (installMethod == null) {
        installMethod = InstallMethod.TEMPLATE;
      }
    }

    /** The widget address, if one is configured. */
    public Optional<String> script() {
      return Optional.ofNullable(scriptUrl);
    }

    /** The widget's origin (e.g. "https://cdn.rainmaker.nu"), for the CSP header. */
    public Optional<String> origin() {
      return script()
          .map(url -> parse(url, "site.rainmaker.script-url"))
          .map(uri -> uri.getScheme() + "://" + uri.getRawAuthority());
    }

    /** True when the page template itself should contain the script tag. */
    public boolean loadedByTemplate() {
      return scriptUrl != null && installMethod == InstallMethod.TEMPLATE;
    }

    /** True when the tag manager should load the script. */
    public boolean loadedByTagManager() {
      return scriptUrl != null && installMethod == InstallMethod.TAG_MANAGER;
    }
  }

  /**
   * Where the Rainmaker script tag comes from (architecture P4). Written in application.yml as
   * "template" or "tag-manager".
   */
  public enum InstallMethod {
    /** One async script tag directly in the page template. */
    TEMPLATE,
    /** The site's tag manager loads the script. */
    TAG_MANAGER
  }

  private static String requireHttpUrl(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must be set");
    }
    URI uri = parse(value, name);
    if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
      throw new IllegalArgumentException(name + " must start with http:// or https://: " + value);
    }
    return value;
  }

  private static URI parse(String value, String name) {
    try {
      URI uri = new URI(value);
      if (uri.getHost() == null) {
        throw new IllegalArgumentException(name + " has no host: " + value);
      }
      return uri;
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException(name + " is not a valid URL: " + value, exception);
    }
  }
}
