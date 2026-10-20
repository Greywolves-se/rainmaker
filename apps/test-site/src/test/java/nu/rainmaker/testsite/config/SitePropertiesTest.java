package nu.rainmaker.testsite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import nu.rainmaker.testsite.config.SiteProperties.InstallMethod;
import nu.rainmaker.testsite.config.SiteProperties.Rainmaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SitePropertiesTest {

  private static final Path CONTENT = Path.of("content");

  @Test
  void removesTrailingSlashFromBaseUrl() {
    SiteProperties site = new SiteProperties("https://uat.example/", CONTENT, null);

    assertThat(site.baseUrl()).isEqualTo("https://uat.example");
    assertThat(site.absoluteUrl("/rss.xml")).isEqualTo("https://uat.example/rss.xml");
  }

  @Test
  void runsWithoutWidgetByDefault() {
    SiteProperties site = new SiteProperties("http://localhost:3000", CONTENT, null);

    assertThat(site.rainmaker().script()).isEmpty();
    assertThat(site.rainmaker().installMethod()).isEqualTo(InstallMethod.TEMPLATE);
    assertThat(site.rainmaker().loadedByTemplate()).isFalse();
    assertThat(site.rainmaker().loadedByTagManager()).isFalse();
  }

  @Test
  void treatsBlankScriptUrlAsNoWidget() {
    assertThat(new Rainmaker("  ", InstallMethod.TEMPLATE).script()).isEmpty();
  }

  @Test
  void knowsWhoLoadsTheWidget() {
    Rainmaker template = new Rainmaker("https://cdn.example/r.js", InstallMethod.TEMPLATE);
    Rainmaker tagManager = new Rainmaker("https://cdn.example/r.js", InstallMethod.TAG_MANAGER);

    assertThat(template.loadedByTemplate()).isTrue();
    assertThat(template.loadedByTagManager()).isFalse();
    assertThat(tagManager.loadedByTagManager()).isTrue();
    assertThat(tagManager.loadedByTemplate()).isFalse();
  }

  @Test
  void derivesTheWidgetOriginForTheSecurityPolicy() {
    Rainmaker widget = new Rainmaker("https://cdn.example:8443/js/r.js", InstallMethod.TEMPLATE);

    assertThat(widget.origin()).contains("https://cdn.example:8443");
  }

  @Test
  void allowsPlainHttpWidgetOnlyOnLocalhost() {
    assertThat(new Rainmaker("http://localhost:5173/r.js", null).script()).isPresent();
    assertThatThrownBy(() -> new Rainmaker("http://cdn.example/r.js", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("https");
  }

  @ParameterizedTest
  @ValueSource(strings = {"javascript:alert(1)", "not a url", "ftp://cdn.example/r.js"})
  void rejectsDangerousOrBrokenWidgetUrls(String url) {
    assertThatThrownBy(() -> new Rainmaker(url, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "ftp://example.com", "example.com"})
  void rejectsBrokenBaseUrls(String url) {
    assertThatThrownBy(() -> new SiteProperties(url, CONTENT, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
