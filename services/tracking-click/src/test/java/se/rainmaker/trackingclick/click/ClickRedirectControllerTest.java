package se.rainmaker.trackingclick.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Opening a click link, {@code GET /c/{token}} (#153, contract 7.5). */
@ExtendWith(OutputCaptureExtension.class)
class ClickRedirectControllerTest {

  private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
  private static final Duration LIFETIME = Duration.ofHours(24);
  private static final UUID ARTICLE = UUID.fromString("0192f3c2-0000-7000-8000-000000000001");
  private static final UUID OFFER = UUID.fromString("0192f3d0-0000-7000-8000-000000000002");
  private static final String DESTINATION = "http://localhost:8083/landing/" + OFFER;
  private static final String READER_IP = "203.0.113.7";
  private static final String FORWARDED_IP = "198.51.100.23";

  private TestClock clock;
  private SignedClickLinks links;
  private ClickStore clicks;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    clock = new TestClock(NOW);
    links =
        new SignedClickLinks(
            "http://localhost:8081",
            LIFETIME,
            "the-signing-key-used-in-the-tests!".getBytes(StandardCharsets.UTF_8),
            clock,
            new UuidV7(clock, new SecureRandom()));
    clicks = mock(ClickStore.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new ClickRedirectController(links, clicks)).build();
  }

  @Test
  void redirectsValidClickToItsDestinationAndStoresIt() throws Exception {
    when(clicks.record(any())).thenReturn(true);

    mockMvc
        .perform(opening(newLink()))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, DESTINATION))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

    ArgumentCaptor<ClickLink> stored = ArgumentCaptor.forClass(ClickLink.class);
    verify(clicks).record(stored.capture());
    assertThat(stored.getValue().articleId()).isEqualTo(ARTICLE);
    assertThat(stored.getValue().offerId()).isEqualTo(OFFER);
  }

  @Test
  void redirectsRepeatedClickToTheSamePlace() throws Exception {
    when(clicks.record(any())).thenReturn(false);

    mockMvc
        .perform(opening(newLink()))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, DESTINATION));
  }

  @Test
  void changedLinkGetsErrorPageAndStoresNothing() throws Exception {
    String link = newLink();
    String changed = link.substring(0, link.length() - 2) + (link.endsWith("A") ? "BB" : "AA");

    mockMvc
        .perform(opening(changed))
        .andExpect(status().isBadRequest())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().contentType("text/html;charset=UTF-8"))
        .andExpect(content().string(containsString("Länken är ogiltig")));

    verifyNoInteractions(clicks);
  }

  @Test
  void garbledTokenGetsErrorPageAndStoresNothing() throws Exception {
    mockMvc
        .perform(get("/c/not-a-click-link").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("The link is invalid")));

    verifyNoInteractions(clicks);
  }

  @Test
  void expiredLinkIsGoneAndStoresNothing() throws Exception {
    String link = newLink();
    clock.advance(LIFETIME);

    mockMvc
        .perform(opening(link))
        .andExpect(status().isGone())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().string(containsString("Länken har gått ut")));

    verifyNoInteractions(clicks);
  }

  @Test
  void doesNotRedirectWhenClickCannotBeStored() throws Exception {
    when(clicks.record(any())).thenThrow(new DataAccessResourceFailureException("database down"));

    mockMvc
        .perform(opening(newLink()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().string(containsString("Please try again")));
  }

  @Test
  void logOfClickHasNoIpAddress(CapturedOutput output) throws Exception {
    when(clicks.record(any())).thenReturn(true);

    mockMvc.perform(fromReader(opening(newLink()))).andExpect(status().isFound());
    mockMvc.perform(fromReader(get("/c/not-a-click-link"))).andExpect(status().isBadRequest());

    assertThat(output)
        .contains("Click stored", "Click link refused")
        .doesNotContain(READER_IP)
        .doesNotContain(FORWARDED_IP);
  }

  private String newLink() {
    return links.create(ARTICLE, OFFER, DESTINATION);
  }

  private static MockHttpServletRequestBuilder opening(String link) {
    return get(link.substring(link.indexOf(SignedClickLinks.PATH_PREFIX)));
  }

  /** A request from a reader's address, also through a proxy that forwards it. */
  private static MockHttpServletRequestBuilder fromReader(MockHttpServletRequestBuilder request) {
    return request
        .header("X-Forwarded-For", FORWARDED_IP)
        .with(
            reader -> {
              reader.setRemoteAddr(READER_IP);
              return reader;
            });
  }
}
