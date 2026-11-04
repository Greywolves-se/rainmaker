package se.rainmaker.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;

/** The Bedrock settings come from cdk.json context and are checked before anything is built. */
class BedrockModelAccessTest {

  static final List<String> EU_REGIONS =
      List.of("eu-north-1", "eu-central-1", "eu-west-1", "eu-west-3", "eu-south-1", "eu-south-2");

  /**
   * The values verified in AWS for Claude Haiku 4.5 on 4 Nov 2026 (#161): the EU inference profile,
   * its foundation model and the six regions it routes to.
   */
  static Map<String, Object> bedrockContext() {
    return Map.of(
        "inferenceProfileId", "eu.anthropic.claude-haiku-4-5-20251001-v1:0",
        "foundationModelId", "anthropic.claude-haiku-4-5-20251001-v1:0",
        "destinationRegions", EU_REGIONS);
  }

  private static App appWith(Map<String, Object> bedrock) {
    return App.Builder.create().context(Map.of("rainmaker:bedrock", bedrock)).build();
  }

  private static Map<String, Object> bedrockContextWith(String key, Object value) {
    Map<String, Object> context = new HashMap<>(bedrockContext());
    context.put(key, value);
    return context;
  }

  /**
   * The other tests build their own context, so without this one cdk.json could name another model
   * and every test would still pass. A wrong id or a missing region means AccessDenied.
   */
  @Test
  void cdkJsonNamesTheVerifiedModelAndRegions() throws IOException {
    String cdkJson = Files.readString(Path.of("cdk.json"));

    assertThat(cdkJson)
        .contains("\"inferenceProfileId\": \"" + bedrockContext().get("inferenceProfileId") + "\"")
        .contains("\"foundationModelId\": \"" + bedrockContext().get("foundationModelId") + "\"");
    EU_REGIONS.forEach(region -> assertThat(cdkJson).contains("\"" + region + "\""));
  }

  @Test
  void readsTheSettingsFromContext() {
    BedrockModelAccess access = BedrockModelAccess.fromContext(appWith(bedrockContext()));

    assertThat(access.inferenceProfileId())
        .isEqualTo("eu.anthropic.claude-haiku-4-5-20251001-v1:0");
    assertThat(access.foundationModelId()).isEqualTo("anthropic.claude-haiku-4-5-20251001-v1:0");
    assertThat(access.destinationRegions()).isEqualTo(EU_REGIONS);
  }

  @Test
  void buildsTheInferenceProfileArnForTheCallingAccountAndRegion() {
    BedrockModelAccess access = BedrockModelAccess.fromContext(appWith(bedrockContext()));

    assertThat(access.inferenceProfileArn("111111111111", "eu-north-1"))
        .isEqualTo(
            "arn:aws:bedrock:eu-north-1:111111111111:inference-profile/"
                + "eu.anthropic.claude-haiku-4-5-20251001-v1:0");
  }

  @Test
  void buildsOneFoundationModelArnPerDestinationRegion() {
    BedrockModelAccess access = BedrockModelAccess.fromContext(appWith(bedrockContext()));

    assertThat(access.foundationModelArns())
        .containsExactlyElementsOf(
            EU_REGIONS.stream()
                .map(
                    region ->
                        "arn:aws:bedrock:"
                            + region
                            + "::foundation-model/anthropic.claude-haiku-4-5-20251001-v1:0")
                .toList());
  }

  @Test
  void refusesMissingSettings() {
    App app = App.Builder.create().build();

    assertThatThrownBy(() -> BedrockModelAccess.fromContext(app))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rainmaker:bedrock");
  }

  @Test
  void refusesBlankModelIds() {
    App app = appWith(bedrockContextWith("inferenceProfileId", " "));

    assertThatThrownBy(() -> BedrockModelAccess.fromContext(app))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inferenceProfileId");
  }

  @Test
  void refusesNoDestinationRegions() {
    App app = appWith(bedrockContextWith("destinationRegions", List.of()));

    assertThatThrownBy(() -> BedrockModelAccess.fromContext(app))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("destinationRegions");
  }

  @Test
  void refusesRegionsOutsideTheEu() {
    App app = appWith(bedrockContextWith("destinationRegions", List.of("eu-north-1", "us-east-1")));

    assertThatThrownBy(() -> BedrockModelAccess.fromContext(app))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("us-east-1");
  }

  @Test
  void refusesProfileOutsideTheEu() {
    App app =
        appWith(
            bedrockContextWith(
                "inferenceProfileId", "us.anthropic.claude-haiku-4-5-20251001-v1:0"));

    assertThatThrownBy(() -> BedrockModelAccess.fromContext(app))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eu.");
  }
}
