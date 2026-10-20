package nu.rainmaker.testsite.web;

import static nu.rainmaker.testsite.web.WebTestSupport.body;
import static nu.rainmaker.testsite.web.WebTestSupport.escaped;
import static nu.rainmaker.testsite.web.WebTestSupport.fetch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unknown addresses, wrong methods and crashes give proper answers, never a stack trace. */
@SpringBootTest(properties = WebTestSupport.BASE_URL_PROPERTY)
@AutoConfigureMockMvc
@Import(ErrorHandlingTest.ExplodingController.class)
class ErrorHandlingTest {

  /** A test-only address that always fails, to check the 500 page. */
  @RestController
  static class ExplodingController {
    @GetMapping("/test-only/explode")
    String explode() {
      throw new IllegalStateException("Simulated failure");
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private MessageSource messages;

  private String message(String key) {
    return messages.getMessage(key, null, Locale.forLanguageTag("sv"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/finns-inte", "/artikel/finns-inte", "/sektion/okand"})
  void unknownPagesShowTheNotFoundPage(String path) throws Exception {
    MvcResult result = fetch(mockMvc, path);

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(result.getResponse().getContentType()).startsWith("text/html");
    assertThat(body(result)).contains(escaped(message("notFound.title")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/static/finns-inte.css",
        "/static/../src/main/resources/application.yml",
        "/static/%2e%2e/application.yml",
        "/static/js/../../application.yml"
      })
  void missingOrForbiddenStaticFilesGivePlainNotFound(String path) throws Exception {
    MvcResult result = fetch(mockMvc, path);

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(result.getResponse().getContentType()).startsWith("text/plain");
    assertThat(body(result)).doesNotContain("server:").doesNotContain("site:");
  }

  @Test
  void postIsRejectedWithAllowHeader() throws Exception {
    MvcResult result = mockMvc.perform(post("/")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(405);
    assertThat(result.getResponse().getHeader("Allow")).contains("GET");
    assertThat(body(result)).isEqualTo(message("methodNotAllowed.text"));
  }

  @Test
  void crashShowsTheErrorPageWithTheNormalLayout() throws Exception {
    MvcResult result = fetch(mockMvc, "/test-only/explode");

    assertThat(result.getResponse().getStatus()).isEqualTo(500);
    assertThat(body(result))
        .contains(escaped(message("error.title")))
        .contains(escaped(message("fiction.label")))
        .doesNotContain("Simulated failure")
        .doesNotContain("Exception");
  }

  @Test
  void queryStringsAreIgnored() throws Exception {
    assertThat(
            fetch(mockMvc, "/artikel/vintraloppet-firar-25-ar?utm_source=test")
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }
}
