package se.rainmaker.infra;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.CfnOIDCProvider;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.WebIdentityPrincipal;
import software.constructs.Construct;

/**
 * Lets GitHub Actions in this repository push the Mission Control images to ECR and roll out the
 * web service on ECS.
 *
 * <p>The provider and role were first created by hand (#59, #84). This stack matches them exactly
 * (checked against {@code aws iam get-role} and {@code get-role-policy}, 15 Oct 2026) so {@code cdk
 * import} adopts them without changes. Both are retained if the stack is deleted, because {@code
 * docker.yml} depends on them.
 */
public class GithubDeployAccessStack extends Stack {

  private static final String GITHUB_OIDC_HOST = "token.actions.githubusercontent.com";
  private static final String STS_AUDIENCE = "sts.amazonaws.com";

  /** Certificate thumbprint stored on the existing provider; kept so the import matches. */
  private static final String GITHUB_OIDC_THUMBPRINT = "ab9d0263244dd0326eb67015705a667e79cfe998";

  /** GitHub sends both subject formats: by name, and by owner and repository ID. */
  private static final List<String> GITHUB_REPOSITORY_SUBJECT_PREFIXES =
      List.of("repo:Greywolves-se/rainmaker", "repo:Greywolves-se@341957527/rainmaker@1416917981");

  /** Secrets the collector reads in staging (#112). Names only; the values never go in code. */
  private static final List<String> COLLECTOR_SECRETS =
      List.of("mission-control/google-drive-service-account", "mission-control/openai-api-key");

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
            .thumbprintList(List.of(GITHUB_OIDC_THUMBPRINT))
            .build();
    githubProvider.applyRemovalPolicy(RemovalPolicy.RETAIN);

    List<String> subjects =
        GITHUB_REPOSITORY_SUBJECT_PREFIXES.stream()
            .map(prefix -> prefix + ":environment:" + githubEnvironment)
            .toList();

    // TreeMap keeps the inline policies in a fixed (alphabetical) order in the template.
    Map<String, PolicyDocument> inlinePolicies =
        new TreeMap<>(
            Map.of(
                "ecr-push-mission-control", ecrPushPolicy(),
                "ecs-deploy-mission-control-web", ecsDeployPolicy(),
                "read-mission-control-secrets", readSecretsPolicy()));

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
                            subjects))))
            .inlinePolicies(inlinePolicies)
            .build();
    deployRole.applyRemovalPolicy(RemovalPolicy.RETAIN);
  }

  /** Lets {@code docker.yml} log in to ECR and push the two Mission Control images. */
  private PolicyDocument ecrPushPolicy() {
    List<String> repositoryArns =
        ECR_REPOSITORIES.stream()
            .map(name -> "arn:aws:ecr:" + getRegion() + ":" + getAccount() + ":repository/" + name)
            .toList();
    return PolicyDocument.Builder.create()
        .statements(
            List.of(
                // Logging in to ECR cannot be limited to a repository.
                PolicyStatement.Builder.create()
                    .sid("EcrLogin")
                    .actions(List.of("ecr:GetAuthorizationToken"))
                    .resources(List.of("*"))
                    .build(),
                PolicyStatement.Builder.create()
                    .sid("EcrPushMissionControl")
                    .actions(
                        List.of(
                            "ecr:BatchCheckLayerAvailability",
                            "ecr:BatchGetImage",
                            "ecr:GetDownloadUrlForLayer",
                            "ecr:InitiateLayerUpload",
                            "ecr:UploadLayerPart",
                            "ecr:CompleteLayerUpload",
                            "ecr:PutImage"))
                    .resources(repositoryArns)
                    .build()))
        .build();
  }

  /** Lets {@code docker.yml} roll the staging web service onto the new image (#84). */
  private PolicyDocument ecsDeployPolicy() {
    String webServiceArn =
        "arn:aws:ecs:" + getRegion() + ":" + getAccount() + ":service/default/mission-control-web";
    return PolicyDocument.Builder.create()
        .statements(
            List.of(
                PolicyStatement.Builder.create()
                    .sid("DeployMissionControlWebStaging")
                    .actions(List.of("ecs:UpdateService", "ecs:DescribeServices"))
                    .resources(List.of(webServiceArn))
                    .build()))
        .build();
  }

  /**
   * Lets the collector step in {@code docker.yml} read the Google Drive and OpenAI secrets (#112),
   * and nothing else in Secrets Manager. The values are set by hand; only the names are in code.
   */
  private PolicyDocument readSecretsPolicy() {
    List<String> secretArns =
        COLLECTOR_SECRETS.stream()
            // Secrets Manager adds "-" and six random characters to the name in the ARN.
            .map(
                name ->
                    "arn:aws:secretsmanager:"
                        + getRegion()
                        + ":"
                        + getAccount()
                        + ":secret:"
                        + name
                        + "-??????")
            .toList();
    return PolicyDocument.Builder.create()
        .statements(
            List.of(
                PolicyStatement.Builder.create()
                    .sid("ReadMissionControlSecrets")
                    .actions(List.of("secretsmanager:GetSecretValue"))
                    .resources(secretArns)
                    .build()))
        .build();
  }
}
