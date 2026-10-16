package se.rainmaker.missioncontrol.collector.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import se.rainmaker.missioncontrol.collector.pipeline.SourceException;
import tools.jackson.databind.JsonNode;

class GithubClientTest {

  private static final String ISSUES = "/repos/example-org/repo/issues";
  private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

  @RegisterExtension
  static WireMockExtension github =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void sendsTheTokenAndTheGithubHeaders() {
    github.stubFor(get(urlPathEqualTo(ISSUES)).willReturn(okJson("[]")));

    client("secret-token").getAll(ISSUES, Map.of("state", "all"));

    github.verify(
        getRequestedFor(urlPathEqualTo(ISSUES))
            .withHeader("Authorization", equalTo("Bearer secret-token"))
            .withHeader("Accept", equalTo("application/vnd.github+json"))
            .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28"))
            .withQueryParam("state", equalTo("all"))
            .withQueryParam("per_page", equalTo("100")));
  }

  @Test
  void followsPaginationLinksAndReturnsAllItems() {
    String page2 = github.baseUrl() + ISSUES + "?page=2";
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .withQueryParam("page", equalTo("2"))
            .willReturn(okJson("[{\"number\": 3}]")));
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .withQueryParam("per_page", equalTo("100"))
            .willReturn(
                okJson("[{\"number\": 1}, {\"number\": 2}]")
                    .withHeader(
                        "Link", "<" + page2 + ">; rel=\"next\", <" + page2 + ">; rel=\"last\"")));

    List<JsonNode> items = client("t").getAll(ISSUES, Map.of());

    assertThat(items).extracting(item -> item.get("number").asInt()).containsExactly(1, 2, 3);
  }

  @Test
  void refusesPaginationLinkToAnotherHost() {
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .willReturn(
                okJson("[]")
                    .withHeader("Link", "<https://elsewhere.example/steal>; rel=\"next\"")));

    assertThatThrownBy(() -> client("t").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessageContaining("pagination link");
  }

  @Test
  void retriesServerErrorAndThenSucceeds() {
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .inScenario("flaky")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("recovered"));
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .inScenario("flaky")
            .whenScenarioStateIs("recovered")
            .willReturn(okJson("[{\"number\": 1}]")));

    assertThat(client("t").getAll(ISSUES, Map.of())).hasSize(1);
    github.verify(2, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  @Test
  void givesUpAfterTheLastAttempt() {
    github.stubFor(get(urlPathEqualTo(ISSUES)).willReturn(aResponse().withStatus(502)));

    assertThatThrownBy(() -> client("t").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessage("GitHub did not answer after 3 attempts (last: HTTP 502)");
    github.verify(3, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  @Test
  void retriesWhenTheServerIsTooSlow() {
    github.stubFor(get(urlPathEqualTo(ISSUES)).willReturn(okJson("[]").withFixedDelay(1_500)));

    assertThatThrownBy(() -> client("t", Duration.ofMillis(300)).getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessage("GitHub did not answer after 3 attempts (last: no answer in time)");
  }

  @Test
  void waitsOutShortRateLimitAndRetries() {
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .inScenario("limited")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(403).withHeader("Retry-After", "0"))
            .willSetStateTo("free"));
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .inScenario("limited")
            .whenScenarioStateIs("free")
            .willReturn(okJson("[]")));

    assertThat(client("t").getAll(ISSUES, Map.of())).isEmpty();
    github.verify(2, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  @Test
  void stopsAtLongRateLimitAndSaysWhenItResets() {
    long resetInOneHour = NOW.plus(Duration.ofHours(1)).getEpochSecond();
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .willReturn(
                aResponse()
                    .withStatus(403)
                    .withHeader("X-RateLimit-Remaining", "0")
                    .withHeader("X-RateLimit-Reset", String.valueOf(resetInOneHour))));

    assertThatThrownBy(() -> client("t").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessage("GitHub rate limit reached; it resets at 13:00 UTC");
    github.verify(1, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  @Test
  void rejectedTokenIsNotRetriedAndNotShown() {
    github.stubFor(get(urlPathEqualTo(ISSUES)).willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> client("secret-token").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessageContaining("rejected the token")
        .hasMessageNotContaining("secret-token");
    github.verify(1, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  @Test
  void accessDeniedNamesThePathButIsNotRetried() {
    github.stubFor(get(urlPathEqualTo(ISSUES)).willReturn(aResponse().withStatus(403)));

    assertThatThrownBy(() -> client("t").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessage("GitHub denied access (HTTP 403) to " + ISSUES + "; check the token's access");
    github.verify(1, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  @Test
  void unreadableRetryAfterIsNotTreatedAsRateLimit() {
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .willReturn(aResponse().withStatus(403).withHeader("Retry-After", "soon")));

    assertThatThrownBy(() -> client("t").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessageStartingWith("GitHub denied access (HTTP 403)");
    github.verify(1, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  @Test
  void movedRepositorySaysWhatToUpdate() {
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .willReturn(
                aResponse()
                    .withStatus(301)
                    .withHeader("Location", github.baseUrl() + "/repositories/42/issues")));

    assertThatThrownBy(() -> client("t").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessageContaining("has moved (HTTP 301)")
        .hasMessageContaining("mission-control.collector.github.repositories");
  }

  @Test
  void getAllRefusesAnswerThatIsNotList() {
    github.stubFor(get(urlPathEqualTo(ISSUES)).willReturn(okJson("{\"message\": \"odd\"}")));

    assertThatThrownBy(() -> client("t").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessage("GitHub answered " + ISSUES + " with something that is not a list");
  }

  @Test
  void getFirstPageReturnsObjectsAndDoesNotFollowLinks() {
    github.stubFor(
        get(urlPathEqualTo(ISSUES))
            .willReturn(
                okJson("{\"total_count\": 1}")
                    .withHeader(
                        "Link", "<" + github.baseUrl() + ISSUES + "?page=2>; rel=\"next\"")));

    assertThat(client("t").getFirstPage(ISSUES, Map.of()).get("total_count").asInt()).isEqualTo(1);
    github.verify(1, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  @Test
  void withoutTokenNothingIsSent() {
    assertThatThrownBy(() -> client(" ").getAll(ISSUES, Map.of()))
        .isInstanceOf(SourceException.class)
        .hasMessage("No GitHub token: set MC_GITHUB_TOKEN");
    github.verify(0, getRequestedFor(urlPathEqualTo(ISSUES)));
  }

  private static GithubClient client(String token) {
    // Generous, so a slow first request in a fresh JVM never counts as a timeout.
    return client(token, Duration.ofSeconds(5));
  }

  private static GithubClient client(String token, Duration readTimeout) {
    GithubProperties properties =
        new GithubProperties(
            URI.create(github.baseUrl()),
            token,
            List.of("example-org/repo"),
            Duration.ofSeconds(1),
            readTimeout,
            3,
            Duration.ofMillis(1),
            Duration.ofSeconds(60),
            5);
    return new GithubClient(properties, Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
