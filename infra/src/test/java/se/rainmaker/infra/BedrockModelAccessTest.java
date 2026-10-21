package se.rainmaker.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;

/** The Bedrock settings come from cdk.json context and are checked before anything is built. */
class BedrockModelAccessTest {

  static final List<String> EU_REGIONS =
      List.of("eu-north-1", "eu-central-1", "eu-west-1", "eu-west-3", "eu-south-1", "eu-south-2");

  /** The values verified in the AWS console on 21 Oct 2026 (#161). */
  static Map<String, Object> bedrockContext() {
    return Map.of(
        "inferenceProfileId", "eu.anthropic.claude-haiku-5-5",
        "foundationModelId", "anthropic.claude-haiku-5-5",
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

  @Test
  void readsTheSettingsFromContext() {
    BedrockModelAccess access = BedrockModelAccess.fromContext(appWith(bedrockContext()));

    assertThat(access.inferenceProfileId()).isEqualTo("eu.anthropic.claude-haiku-5-5");
    assertThat(access.foundationModelId()).isEqualTo("anthropic.claude-haiku-5-5");
    assertThat(access.destinationRegions()).isEqualTo(EU_REGIONS);
  }

  @Test
  void buildsTheInferenceProfileArnForTheCallingAccountAndRegion() {
    BedrockModelAccess access = BedrockModelAccess.fromContext(appWith(bedrockContext()));

    assertThat(access.inferenceProfileArn("111111111111", "eu-north-1"))
        .isEqualTo(
            "arn:aws:bedrock:eu-north-1:111111111111:inference-profile/"
                + "eu.anthropic.claude-haiku-5-5");
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
                            + "::foundation-model/anthropic.claude-haiku-5-5")
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
    App app = appWith(bedrockContextWith("inferenceProfileId", "us.anthropic.claude-haiku-5-5"));

    assertThatThrownBy(() -> BedrockModelAccess.fromContext(app))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eu.");
  }
}
