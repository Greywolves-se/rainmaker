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
 * The G2 content app's role may call Claude Haiku through the EU inference profile, and nothing
 * else (#161). The account is a placeholder; the real one is never in the repository.
 */
class BedrockAccessStackTest {

  private static final String ACCOUNT = "111111111111";

  private static final String PROFILE_ARN =
      "arn:aws:bedrock:eu-north-1:" + ACCOUNT + ":inference-profile/eu.anthropic.claude-haiku-5-5";

  private Template template;

  @BeforeEach
  void synthesizeDevStack() {
    App app =
        App.Builder.create()
            .context(Map.of("rainmaker:bedrock", BedrockModelAccessTest.bedrockContext()))
            .build();
    StackProps props =
        StackProps.builder()
            .env(Environment.builder().account(ACCOUNT).region("eu-north-1").build())
            .build();
    BedrockAccessStack stack =
        new BedrockAccessStack(
            app, "BedrockAccess-dev", props, BedrockModelAccess.fromContext(app));
    template = Template.fromStack(stack);
  }

  @Test
  void roleMayOnlyInvokeClaudeThroughTheEuProfile() {
    List<String> foundationModelArns =
        BedrockModelAccessTest.EU_REGIONS.stream()
            .map(
                region ->
                    "arn:aws:bedrock:" + region + "::foundation-model/anthropic.claude-haiku-5-5")
            .toList();

    template.hasResourceProperties(
        "AWS::IAM::Role",
        Map.of(
            "Policies",
            Match.exact(
                List.of(
                    Map.of(
                        "PolicyName",
                        "invoke-claude-through-eu-profile",
                        "PolicyDocument",
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
                                        Map.of("bedrock:InferenceProfileArn", PROFILE_ARN))))))))));
  }

  @Test
  void roleCanOnlyBeAssumedByEcsTasksInTheSameAccount() {
    template.hasResourceProperties(
        "AWS::IAM::Role",
        Map.of(
            "RoleName",
            "rainmaker-g2-content-app",
            "AssumeRolePolicyDocument",
            Match.exact(
                Map.of(
                    "Version",
                    "2012-10-17",
                    "Statement",
                    List.of(
                        Map.of(
                            "Action",
                            "sts:AssumeRole",
                            "Effect",
                            "Allow",
                            "Principal",
                            Map.of("Service", "ecs-tasks.amazonaws.com"),
                            "Condition",
                            Map.of("StringEquals", Map.of("aws:SourceAccount", ACCOUNT))))))));
  }

  @Test
  void createsOnlyTheRole() {
    template.resourceCountIs("AWS::IAM::Role", 1);
    template.resourceCountIs("AWS::IAM::Policy", 0);
    template.resourceCountIs("AWS::IAM::ManagedPolicy", 0);
    template.hasResourceProperties("AWS::IAM::Role", Map.of("ManagedPolicyArns", Match.absent()));
  }

  @Test
  void keepsTheRoleIfTheStackIsDeleted() {
    template.hasResource(
        "AWS::IAM::Role", Map.of("DeletionPolicy", "Retain", "UpdateReplacePolicy", "Retain"));
  }
}
