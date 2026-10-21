package se.rainmaker.infra;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

/**
 * Developers get the same narrow Bedrock access as the G2 role, through an IAM Identity Center
 * permission set, so they never need access keys (#161).
 */
class DeveloperAccessStackTest {

  private static final String ACCOUNT = "111111111111";
  private static final String INSTANCE_ARN = "arn:aws:sso:::instance/ssoins-0000000000000000";

  private static final String PROFILE_ARN =
      "arn:aws:bedrock:eu-north-1:" + ACCOUNT + ":inference-profile/eu.anthropic.claude-haiku-5-5";

  private Template template;

  @BeforeEach
  void synthesize() {
    App app =
        App.Builder.create()
            .context(Map.of("rainmaker:bedrock", BedrockModelAccessTest.bedrockContext()))
            .build();
    StackProps props =
        StackProps.builder()
            .env(Environment.builder().account(ACCOUNT).region("eu-north-1").build())
            .build();
    template =
        Template.fromStack(
            new DeveloperAccessStack(
                app,
                "DeveloperAccess-dev",
                props,
                BedrockModelAccess.fromContext(app),
                INSTANCE_ARN));
  }

  @Test
  void permissionSetAllowsOnlyTheSameBedrockAccessAsTheG2Role() {
    List<String> foundationModelArns =
        BedrockModelAccessTest.EU_REGIONS.stream()
            .map(
                region ->
                    "arn:aws:bedrock:" + region + "::foundation-model/anthropic.claude-haiku-5-5")
            .toList();

    template.hasResourceProperties(
        "AWS::SSO::PermissionSet",
        Map.of(
            "InlinePolicy",
            Match.exact(
                Map.of(
                    "Version",
                    "2012-10-17",
                    "Statement",
                    List.of(
                        Map.of(
                            "Sid",
                            "InvokeThroughEuInferenceProfile",
                            "Effect",
                            "Allow",
                            "Action",
                            "bedrock:InvokeModel",
                            "Resource",
                            PROFILE_ARN),
                        Map.of(
                            "Sid",
                            "FoundationModelOnlyThroughTheProfile",
                            "Effect",
                            "Allow",
                            "Action",
                            "bedrock:InvokeModel",
                            "Resource",
                            foundationModelArns,
                            "Condition",
                            Map.of(
                                "StringEquals",
                                Map.of("bedrock:InferenceProfileArn", PROFILE_ARN))))))));
  }

  @Test
  void permissionSetHasNameShortSessionAndNoManagedPolicies() {
    template.hasResourceProperties(
        "AWS::SSO::PermissionSet",
        Map.of(
            "Name",
            "RainmakerBedrockDeveloper",
            "InstanceArn",
            INSTANCE_ARN,
            "SessionDuration",
            "PT8H",
            "ManagedPolicies",
            Match.absent(),
            "CustomerManagedPolicyReferences",
            Match.absent()));
  }

  @Test
  void assignsNobody() {
    template.resourceCountIs("AWS::SSO::PermissionSet", 1);
    template.resourceCountIs("AWS::SSO::Assignment", 0);
  }
}
