package se.rainmaker.infra;

import java.util.List;
import java.util.Map;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.CfnOIDCProvider;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.WebIdentityPrincipal;
import software.constructs.Construct;

/**
 * Lets GitHub Actions in this repository push the Mission Control images to ECR.
 *
 * <p>The provider, role and policy were first created by hand (#59); this stack is written to match
 * them so they can be imported with {@code cdk import} (#61).
 */
public class GithubDeployAccessStack extends Stack {

  private static final String GITHUB_OIDC_HOST = "token.actions.githubusercontent.com";
  private static final String GITHUB_REPOSITORY = "Greywolves-se/rainmaker";
  private static final String STS_AUDIENCE = "sts.amazonaws.com";
  private static final List<String> ECR_REPOSITORIES =
      List.of("rainmaker-mission-control-collector", "rainmaker-mission-control-web");

  /**
   * Creates the stack for one account.
   *
   * @param githubEnvironment the GitHub environment (e.g. "staging") whose jobs may assume the role
   */
  public GithubDeployAccessStack(
      Construct scope, String id, StackProps props, String githubEnvironment) {
    super(scope, id, props);

    CfnOIDCProvider githubProvider =
        CfnOIDCProvider.Builder.create(this, "GithubOidcProvider")
            .url("https://" + GITHUB_OIDC_HOST)
            .clientIdList(List.of(STS_AUDIENCE))
            .build();

    Role deployRole =
        Role.Builder.create(this, "GithubActionsRole")
            .roleName("github-actions-rainmaker")
            .assumedBy(
                new WebIdentityPrincipal(
                    githubProvider.getAttrArn(),
                    Map.of(
                        "StringEquals",
                        Map.of(
                            GITHUB_OIDC_HOST + ":aud",
                            STS_AUDIENCE,
                            GITHUB_OIDC_HOST + ":sub",
                            "repo:" + GITHUB_REPOSITORY + ":environment:" + githubEnvironment))))
            .build();

    ManagedPolicy.Builder.create(this, "EcrPushPolicy")
        .managedPolicyName("ecr-push-mission-control")
        .roles(List.of(deployRole))
        .statements(List.of(ecrLogin(), ecrPush()))
        .build();
  }

  /** Logging in to ECR cannot be limited to a repository. */
  private static PolicyStatement ecrLogin() {
    return PolicyStatement.Builder.create()
        .actions(List.of("ecr:GetAuthorizationToken"))
        .resources(List.of("*"))
        .build();
  }

  private PolicyStatement ecrPush() {
    List<String> repositoryArns =
        ECR_REPOSITORIES.stream()
            .map(name -> "arn:aws:ecr:" + getRegion() + ":" + getAccount() + ":repository/" + name)
            .toList();
    return PolicyStatement.Builder.create()
        .actions(
            List.of(
                "ecr:BatchCheckLayerAvailability",
                "ecr:BatchGetImage",
                "ecr:CompleteLayerUpload",
                "ecr:GetDownloadUrlForLayer",
                "ecr:InitiateLayerUpload",
                "ecr:PutImage",
                "ecr:UploadLayerPart"))
        .resources(repositoryArns)
        .build();
  }
}
