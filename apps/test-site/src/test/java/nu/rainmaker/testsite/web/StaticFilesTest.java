package nu.rainmaker.testsite.web;

import static nu.rainmaker.testsite.web.WebTestSupport.fetch;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The browser scripts are served correctly and follow the site's safety rules. */
@SpringBootTest(properties = WebTestSupport.BASE_URL_PROPERTY)
@AutoConfigureMockMvc
class StaticFilesTest {

  private static final Pattern RELATIVE_IMPORT = Pattern.compile("from '\\./([^']+)'");
  private static final Pattern RAW_HTML =
      Pattern.compile("\\.(innerHTML|outerHTML)\\s*\\+?=|insertAdjacentHTML|document\\.write");

  @Autowired private MockMvc mockMvc;

  private String script(String name) throws Exception {
    return fetch(mockMvc, "/static/js/" + name)
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "tcf-stub.js",
        "site.js",
        "consent.js",
        "consent-core.js",
        "ads.js",
        "paywall.js",
        "sticky-header.js",
        "tag-manager.js",
        "vintra-stats.js",
        "widget-stub.js"
      })
  void scriptIsServedAsJavaScript(String name) throws Exception {
    MvcResult result = fetch(mockMvc, "/static/js/" + name);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType()).startsWith("text/javascript");
  }

  @ParameterizedTest
  @ValueSource(strings = {"site.js", "consent.js"})
  void moduleOnlyImportsFilesThatExist(String name) throws Exception {
    Matcher imports = RELATIVE_IMPORT.matcher(script(name));
    int count = 0;
    while (imports.find()) {
      count++;
      assertThat(fetch(mockMvc, "/static/js/" + imports.group(1)).getResponse().getStatus())
          .as(name + " imports " + imports.group(1))
          .isEqualTo(200);
    }
    assertThat(count).isPositive();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "consent.js",
        "ads.js",
        "paywall.js",
        "sticky-header.js",
        "tag-manager.js",
        "widget-stub.js"
      })
  void scriptNeverWritesRawHtml(String name) throws Exception {
    assertThat(script(name)).doesNotContainPattern(RAW_HTML);
  }
}
