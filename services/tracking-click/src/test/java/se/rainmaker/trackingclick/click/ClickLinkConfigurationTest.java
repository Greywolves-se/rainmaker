package se.rainmaker.trackingclick.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import se.rainmaker.trackingclick.api.ClickLinks;

/** The settings under {@code rainmaker.tracking-click.link.*} (#153). */
@ExtendWith(OutputCaptureExtension.class)
class ClickLinkConfigurationTest {

  private static final String KEY = "a-signing-key-that-is-long-enough!";
  private static final Duration LIFETIME = Duration.ofHours(24);

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(ClickLinkConfiguration.class)
          .withPropertyValues(
              "rainmaker.tracking-click.link.base-url=http://localhost:8081",
              "rainmaker.tracking-click.link.lifetime=24h");

  @Test
  void usesTheConfiguredKeyQuietly(CapturedOutput output) {
    runner
        .withPropertyValues("rainmaker.tracking-click.link.signing-key=" + KEY)
        .run(context -> assertThat(context).hasSingleBean(ClickLinks.class));

    assertThat(output).doesNotContain("RAINMAKER_CLICK_SIGNING_KEY").doesNotContain(KEY);
  }

  /** Several G1s, or a restart, would each sign with their own key and lose paid clicks (#189). */
  @Test
  void refusesToStartWithoutKey() {
    runner
        .withPropertyValues("rainmaker.tracking-click.link.signing-key=")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("RAINMAKER_CLICK_SIGNING_KEY")
                    .hasMessageContaining("local"));
  }

  @Test
  void usesRandomKeyForThisRunOnlyWhenAllowedForLocalRuns(CapturedOutput output) {
    runner
        .withPropertyValues(
            "rainmaker.tracking-click.link.signing-key=",
            "rainmaker.tracking-click.link.allow-random-signing-key=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ClickLinks.class);
              String link =
                  context
                      .getBean(ClickLinks.class)
                      .create(
                          UUID.randomUUID(), UUID.randomUUID(), "http://localhost:8083/landing/1");
              assertThat(link).startsWith("http://localhost:8081/c/");
            });

    assertThat(output).contains("WARN").contains("RAINMAKER_CLICK_SIGNING_KEY");
  }

  /** No default address: one that wasn't set would send every reader to localhost (#189). */
  @Test
  void refusesToStartWithoutAddress() {
    new ApplicationContextRunner()
        .withUserConfiguration(ClickLinkConfiguration.class)
        .withPropertyValues(
            "rainmaker.tracking-click.link.lifetime=24h",
            "rainmaker.tracking-click.link.signing-key=" + KEY)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("rainmaker.tracking-click.link.base-url"));
  }

  @Test
  void settingsNeverPrintTheKey() {
    ClickLinkProperties settings =
        new ClickLinkProperties("http://localhost:8081", LIFETIME, KEY, false);

    assertThat(settings.toString()).doesNotContain(KEY).contains("signingKey=<set>");
  }

  /** A newline from an env file on one G1 but not another would make their keys differ. */
  @Test
  void trimsTheKey() {
    ClickLinkProperties settings =
        new ClickLinkProperties("http://localhost:8081", LIFETIME, " " + KEY + "\n", false);

    assertThat(settings.signingKey()).isEqualTo(KEY);
  }

  @Test
  void refusesKeyOfOnlySpaces() {
    assertThatThrownBy(
            () -> new ClickLinkProperties("http://localhost:8081", LIFETIME, "   \n", true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RAINMAKER_CLICK_SIGNING_KEY");
  }

  @Test
  void refusesToStartWithShortKey() {
    runner
        .withPropertyValues("rainmaker.tracking-click.link.signing-key=too-short")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void refusesToStartWithoutHttpAddress() {
    runner
        .withPropertyValues(
            "rainmaker.tracking-click.link.signing-key=" + KEY,
            "rainmaker.tracking-click.link.base-url=localhost:8081")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void acceptsAddressWithUpperCaseScheme() {
    ClickLinkProperties settings =
        new ClickLinkProperties("HTTPS://clicks.example.se", LIFETIME, KEY, false);

    assertThat(settings.baseUrl()).isEqualTo("HTTPS://clicks.example.se");
  }

  @Test
  void refusesToStartWithoutPositiveLifetime() {
    runner
        .withPropertyValues(
            "rainmaker.tracking-click.link.signing-key=" + KEY,
            "rainmaker.tracking-click.link.lifetime=0s")
        .run(context -> assertThat(context).hasFailed());
  }
}
