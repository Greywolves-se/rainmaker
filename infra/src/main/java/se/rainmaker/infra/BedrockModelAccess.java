package se.rainmaker.infra;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.constructs.Construct;

/**
 * Which Claude model Rainmaker may call through Amazon Bedrock, and the IAM statements that allow
 * exactly that (#161, contract D-8). The values come from the {@code rainmaker:bedrock} context in
 * {@code cdk.json}, so changing the model is a configuration change, not a code change.
 *
 * <p>The model is called through an EU cross-region inference profile. Bedrock routes each request
 * to one of the profile's destination regions, so the caller needs the profile and the foundation
 * model in every one of those regions (Bedrock user guide, "Prerequisites for inference profiles").
 * The second statement only applies when the request comes through the profile ({@code
 * bedrock:InferenceProfileArn}), so the base model cannot be called directly, and the resource list
 * keeps every call inside the EU regions.
 *
 * @param inferenceProfileId the EU inference profile, e.g. {@code eu.anthropic.claude-haiku-5-5}
 * @param foundationModelId the model the profile routes to, e.g. {@code anthropic.claude-haiku-5-5}
 * @param destinationRegions the regions the profile routes to; all must be EU regions
 */
public record BedrockModelAccess(
    String inferenceProfileId, String foundationModelId, List<String> destinationRegions) {

  static final String CONTEXT_KEY = "rainmaker:bedrock";

  /** The only IAM action needed. It also covers the Converse API; streaming is not used. */
  static final String INVOKE_MODEL = "bedrock:InvokeModel";

  private static final Pattern EU_REGION = Pattern.compile("^eu-[a-z]+-\\d+$");

  /** Checks that the profile and every destination region are in the EU. */
  public BedrockModelAccess {
    if (!inferenceProfileId.startsWith("eu.")) {
      throw new IllegalArgumentException(
          "CDK context \""
              + CONTEXT_KEY
              + "\": inferenceProfileId must be an EU profile (\"eu.\"): "
              + inferenceProfileId);
    }
    for (String region : destinationRegions) {
      if (!EU_REGION.matcher(region).matches()) {
        throw new IllegalArgumentException(
            "CDK context \"" + CONTEXT_KEY + "\": not an EU region: " + region);
      }
    }
    destinationRegions = List.copyOf(destinationRegions);
  }

  /** Reads the settings from the {@code rainmaker:bedrock} context. */
  public static BedrockModelAccess fromContext(Construct scope) {
    Map<?, ?> settings = ContextValues.requireObject(scope, CONTEXT_KEY);
    return new BedrockModelAccess(
        ContextValues.requireText(settings, CONTEXT_KEY, "inferenceProfileId"),
        ContextValues.requireText(settings, CONTEXT_KEY, "foundationModelId"),
        ContextValues.requireTextList(settings, CONTEXT_KEY, "destinationRegions"));
  }

  /** The profile's ARN in the account and region the caller sends its requests to. */
  public String inferenceProfileArn(String account, String region) {
    return "arn:aws:bedrock:" + region + ":" + account + ":inference-profile/" + inferenceProfileId;
  }

  /** The foundation model's ARN in each destination region; these ARNs have no account. */
  public List<String> foundationModelArns() {
    return destinationRegions.stream()
        .map(region -> "arn:aws:bedrock:" + region + "::foundation-model/" + foundationModelId)
        .toList();
  }

  /** Allows invoking the model through the profile, and in no other way. */
  public List<PolicyStatement> invokeStatements(String account, String region) {
    String profileArn = inferenceProfileArn(account, region);
    return List.of(
        PolicyStatement.Builder.create()
            .sid("InvokeThroughEuInferenceProfile")
            .actions(List.of(INVOKE_MODEL))
            .resources(List.of(profileArn))
            .build(),
        PolicyStatement.Builder.create()
            .sid("FoundationModelOnlyThroughTheProfile")
            .actions(List.of(INVOKE_MODEL))
            .resources(foundationModelArns())
            .conditions(Map.of("StringEquals", Map.of("bedrock:InferenceProfileArn", profileArn)))
            .build());
  }
}
