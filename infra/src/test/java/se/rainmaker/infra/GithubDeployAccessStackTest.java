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

class GithubDeployAccessStackTest {

  /** The only GitHub OIDC subject that may assume the role in staging. */
  private static final String STAGING_SUBJECT = "repo:Greywolves-se/rainmaker:environment:staging";

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
            "github-actions-rainmaker",
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
                                            STAGING_SUBJECT))))))))));
  }

  @Test
  void trustsTheGithubActionsOidcProvider() {
    template.hasResourceProperties(
        "AWS::IAM::OIDCProvider",
        Map.of(
            "Url",
            "https://token.actions.githubusercontent.com",
            "ClientIdList",
            List.of("sts.amazonaws.com")));
  }

  @Test
  void attachesTheEcrPushPolicy() {
    template.hasResourceProperties(
        "AWS::IAM::ManagedPolicy", Map.of("ManagedPolicyName", "ecr-push-mission-control"));
  }
}
