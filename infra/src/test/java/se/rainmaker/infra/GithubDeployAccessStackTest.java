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
 * Checks the stack against the role and provider that exist in the staging account. The expected
 * values are copied from {@code aws iam get-role}, {@code get-role-policy} and {@code
 * get-open-id-connect-provider} (15 Oct 2026), so {@code cdk import} adopts them unchanged.
 */
class GithubDeployAccessStackTest {

  /** The only GitHub OIDC subjects that may assume the role in staging (name- and ID-based). */
  private static final List<String> STAGING_SUBJECTS =
      List.of(
          "repo:Greywolves-se/rainmaker:environment:staging",
          "repo:Greywolves-se@341957527/rainmaker@1416917981:environment:staging");

  /** Secrets Manager adds six random characters to a secret's ARN, hence "-??????" below. */
  private static final String SECRET_ARN = "arn:aws:secretsmanager:eu-north-1:659947636280:secret:";

  private static final String ECR_REPOSITORY_ARN =
      "arn:aws:ecr:eu-north-1:659947636280:repository/";

  private Template template;

  @BeforeEach
  void synthesizeStagingStack() {
    App app = new App();
    StackProps props =
        StackProps.builder()
            .env(Environment.builder().account("659947636280").region("eu-north-1").build())
            .build();
    GithubDeployAccessStack stack =
        new GithubDeployAccessStack(app, "GithubDeployAccess-staging", props, "staging");
    template = Template.fromStack(stack);
  }

  @Test
  void roleCanOnlyBeAssumedFromTheStagingEnvironment() {
    template.hasResourceProperties(
        "AWS::IAM::Role",
        Map.of(
            "RoleName",
            "github-actions-rainmaker-RED-TEST",
            "AssumeRolePolicyDocument",
            Match.objectLike(
                Map.of(
                    "Statement",
                    List.of(
                        Match.objectLike(
                            Map.of(
                                "Action",
                                "sts:AssumeRoleWithWebIdentity",
                                "Condition",
                                Match.exact(
                                    Map.of(
                                        "StringEquals",
                                        Map.of(
                                            "token.actions.githubusercontent.com:aud",
                                            "sts.amazonaws.com",
                                            "token.actions.githubusercontent.com:sub",
                                            STAGING_SUBJECTS))))))))));
  }

  @Test
  void trustsTheGithubActionsOidcProvider() {
    template.hasResourceProperties(
        "AWS::IAM::OIDCProvider",
        Match.exact(
            Map.of(
                "Url",
                "https://token.actions.githubusercontent.com",
                "ClientIdList",
                List.of("sts.amazonaws.com"),
                "ThumbprintList",
                List.of("ab9d0263244dd0326eb67015705a667e79cfe998"))));
  }

  @Test
  void roleHasTheEcrAndEcsPoliciesInline() {
    template.hasResourceProperties(
        "AWS::IAM::Role",
        Map.of(
            "Policies",
            Match.exact(
                List.of(
                    Map.of(
                        "PolicyName",
                        "ecr-push-mission-control",
                        "PolicyDocument",
                        Map.of(
                            "Version",
                            "2012-10-17",
                            "Statement",
                            List.of(
                                Map.of(
                                    "Sid",
                                    "EcrLogin",
                                    "Effect",
                                    "Allow",
                                    "Action",
                                    "ecr:GetAuthorizationToken",
                                    "Resource",
                                    "*"),
                                Map.of(
                                    "Sid",
                                    "EcrPushMissionControl",
                                    "Effect",
                                    "Allow",
                                    "Action",
                                    List.of(
                                        "ecr:BatchCheckLayerAvailability",
                                        "ecr:BatchGetImage",
                                        "ecr:GetDownloadUrlForLayer",
                                        "ecr:InitiateLayerUpload",
                                        "ecr:UploadLayerPart",
                                        "ecr:CompleteLayerUpload",
                                        "ecr:PutImage"),
                                    "Resource",
                                    List.of(
                                        ECR_REPOSITORY_ARN + "rainmaker-mission-control-collector",
                                        ECR_REPOSITORY_ARN + "rainmaker-mission-control-web"))))),
                    Map.of(
                        "PolicyName",
                        "ecs-deploy-mission-control-web",
                        "PolicyDocument",
                        Map.of(
                            "Version",
                            "2012-10-17",
                            "Statement",
                            List.of(
                                Map.of(
                                    "Sid",
                                    "DeployMissionControlWebStaging",
                                    "Effect",
                                    "Allow",
                                    "Action",
                                    List.of("ecs:UpdateService", "ecs:DescribeServices"),
                                    "Resource",
                                    "arn:aws:ecs:eu-north-1:659947636280:service/default/"
                                        + "mission-control-web")))),
                    Map.of(
                        "PolicyName",
                        "read-mission-control-secrets",
                        "PolicyDocument",
                        Map.of(
                            "Version",
                            "2012-10-17",
                            "Statement",
                            List.of(
                                Map.of(
                                    "Sid",
                                    "ReadMissionControlSecrets",
                                    "Effect",
                                    "Allow",
                                    "Action",
                                    "secretsmanager:GetSecretValue",
                                    "Resource",
                                    SECRET_ARN + "mission-control/openai-api-key-??????"))))))));
  }

  @Test
  void hasNoManagedPolicy() {
    template.resourceCountIs("AWS::IAM::ManagedPolicy", 0);
  }

  @Test
  void keepsTheRoleAndProviderIfTheStackIsDeleted() {
    Map<String, Object> retained =
        Map.of("DeletionPolicy", "Retain", "UpdateReplacePolicy", "Retain");
    template.hasResource("AWS::IAM::Role", retained);
    template.hasResource("AWS::IAM::OIDCProvider", retained);
  }
}
