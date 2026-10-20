package se.rainmaker.testsite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static se.rainmaker.testsite.web.WebTestSupport.fetch;
import static se.rainmaker.testsite.web.WebTestSupport.page;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** The two ways to install the Rainmaker widget (architecture P4). */
class RainmakerInstallTest {

  private static final String WIDGET = "https://cdn.rainmaker.example/rainmaker.js";

  /** Method 1: one async script tag in the page template. */
  @Nested
  @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
  @SpringBootTest(
      properties = {
        WebTestSupport.BASE_URL_PROPERTY,
        "site.rainmaker.script-url=" + WIDGET,
        "site.rainmaker.install-method=template"
      })
  @AutoConfigureMockMvc
  class Template {

    @Autowired private MockMvc mockMvc;

    @Test
    void addsExactlyOneAsyncScriptTagAndAllowsItsOrigin() throws Exception {
      String html = page(mockMvc, "/artikel/vintraloppet-firar-25-ar");

      assertThat(html).containsOnlyOnce("<script async src=\"" + WIDGET + "\"></script>");
      assertThat(fetch(mockMvc, "/").getResponse().getHeader("Content-Security-Policy"))
          .contains("script-src 'self' https://cdn.rainmaker.example;");
    }

    @Test
    void doesNotAlsoPutTheWidgetInTheTagManager() throws Exception {
      assertThat(page(mockMvc, "/tag-manager/container.json")).doesNotContain("rainmaker");
    }
  }

  /** Method 2: the site's tag manager loads the widget. */
  @Nested
  @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
  @SpringBootTest(
      properties = {
        WebTestSupport.BASE_URL_PROPERTY,
        "site.rainmaker.script-url=" + WIDGET,
        "site.rainmaker.install-method=tag-manager"
      })
  @AutoConfigureMockMvc
  class TagManager {

    @Autowired private MockMvc mockMvc;

    @Test
    void putsTheWidgetInTheContainerOnPageView() throws Exception {
      assertThat(page(mockMvc, "/tag-manager/container.json"))
          .contains("{\"name\":\"rainmaker\",\"src\":\"" + WIDGET + "\",\"trigger\":\"pageview\"}");
    }

    @Test
    void leavesTheScriptTagOutOfThePageButStillAllowsTheOrigin() throws Exception {
      assertThat(page(mockMvc, "/")).doesNotContain(WIDGET);
      assertThat(fetch(mockMvc, "/").getResponse().getHeader("Content-Security-Policy"))
          .contains("script-src 'self' https://cdn.rainmaker.example;");
    }
  }
}
