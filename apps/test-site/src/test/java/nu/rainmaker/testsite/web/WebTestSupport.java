package nu.rainmaker.testsite.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.HtmlUtils;

/** Helpers shared by the web tests. */
final class WebTestSupport {

  /** Used by all web tests, so they can share one Spring context. */
  static final String BASE_URL_PROPERTY = "site.base-url=https://test.example";

  private WebTestSupport() {}

  /** GETs a page and returns the response. */
  static MvcResult fetch(MockMvc mockMvc, String path) throws Exception {
    return mockMvc.perform(get(path)).andReturn();
  }

  /** GETs a page and returns its body with all whitespace runs collapsed to one space. */
  static String page(MockMvc mockMvc, String path) throws Exception {
    return body(fetch(mockMvc, path));
  }

  /** The response body with whitespace collapsed (Thymeleaf keeps line breaks in tags). */
  static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8).replaceAll("\\s+", " ");
  }

  /** Escapes text the way the templates do (only &amp; &lt; &gt; &quot; &#39;). */
  static String escaped(String text) {
    return HtmlUtils.htmlEscape(text, "UTF-8").replaceAll("\\s+", " ");
  }

  /** The part of a page from {@code <article class="article"} to {@code </article>}. */
  static String articleElement(String html) {
    int start = html.indexOf("<article class=\"article\"");
    return html.substring(start, html.indexOf("</article>", start));
  }
}
