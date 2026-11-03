package se.rainmaker.testsite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.rainmaker.testsite.config.SiteProperties.InstallMethod;
import se.rainmaker.testsite.config.SiteProperties.Rainmaker;

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
    assertThat(new Rainmaker("  ", InstallMethod.TEMPLATE, null).script()).isEmpty();
  }

  @Test
  void knowsWhoLoadsTheWidget() {
    Rainmaker template = new Rainmaker("https://cdn.example/r.js", InstallMethod.TEMPLATE, null);
    Rainmaker tagManager =
        new Rainmaker("https://cdn.example/r.js", InstallMethod.TAG_MANAGER, null);

    assertThat(template.loadedByTemplate()).isTrue();
    assertThat(template.loadedByTagManager()).isFalse();
    assertThat(tagManager.loadedByTagManager()).isTrue();
    assertThat(tagManager.loadedByTemplate()).isFalse();
  }

  @Test
  void derivesTheWidgetOriginForTheSecurityPolicy() {
    Rainmaker widget =
        new Rainmaker("https://cdn.example:8443/js/r.js", InstallMethod.TEMPLATE, null);

    assertThat(widget.origin()).contains("https://cdn.example:8443");
  }

  @Test
  void allowsPlainHttpWidgetOnlyOnLocalhost() {
    assertThat(new Rainmaker("http://localhost:5173/r.js", null, null).script()).isPresent();
    assertThatThrownBy(() -> new Rainmaker("http://cdn.example/r.js", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("https");
  }

  @ParameterizedTest
  @ValueSource(strings = {"javascript:alert(1)", "not a url", "ftp://cdn.example/r.js"})
  void rejectsDangerousOrBrokenWidgetUrls(String url) {
    assertThatThrownBy(() -> new Rainmaker(url, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** The end-to-end test (#154) shortens the widget's qualified reading on the script tag. */
  @Test
  void passesTheQualifiedReadingSecondsToTheScriptTag() {
    Rainmaker shortened = new Rainmaker("https://cdn.example/r.js", InstallMethod.TEMPLATE, 2);

    assertThat(shortened.scriptAttributes())
        .containsExactly(Map.entry("data-qualified-reading-seconds", "2"));
  }

  @Test
  void addsNoScriptAttributeWithoutQualifiedReadingSeconds() {
    assertThat(new Rainmaker("https://cdn.example/r.js", null, null).scriptAttributes()).isEmpty();
  }

  /** The same range as the widget's (1 second to an hour), but a wrong value stops the site. */
  @ParameterizedTest
  @ValueSource(ints = {0, -1, 3601})
  void rejectsQualifiedReadingSecondsOutsideTheWidgetsRange(int seconds) {
    assertThatThrownBy(() -> new Rainmaker("https://cdn.example/r.js", null, seconds))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("qualified-reading-seconds");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "ftp://example.com", "example.com"})
  void rejectsBrokenBaseUrls(String url) {
    assertThatThrownBy(() -> new SiteProperties(url, CONTENT, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
