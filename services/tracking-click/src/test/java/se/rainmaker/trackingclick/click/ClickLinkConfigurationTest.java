package se.rainmaker.trackingclick.click;

import static org.assertj.core.api.Assertions.assertThat;

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

  @Test
  void usesRandomKeyForThisRunWhenNoneIsSet(CapturedOutput output) {
    runner
        .withPropertyValues("rainmaker.tracking-click.link.signing-key=")
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

  @Test
  void settingsNeverPrintTheKey() {
    ClickLinkProperties settings =
        new ClickLinkProperties("http://localhost:8081", Duration.ofHours(24), KEY);

    assertThat(settings.toString()).doesNotContain(KEY).contains("signingKey=<set>");
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
  void refusesToStartWithoutPositiveLifetime() {
    runner
        .withPropertyValues(
            "rainmaker.tracking-click.link.signing-key=" + KEY,
            "rainmaker.tracking-click.link.lifetime=0s")
        .run(context -> assertThat(context).hasFailed());
  }
}
