package se.rainmaker.infra;

import java.util.Map;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

/**
 * The IAM role {@code g2-content-app} uses to classify articles with Claude through Amazon Bedrock
 * (#161, #148). It may only invoke the configured model through the EU inference profile; see
 * {@link BedrockModelAccess}.
 *
 * <p>The role itself costs nothing. Bedrock is billed per call, and only something running with
 * this role, or a developer with the same access, can make one.
 */
public class BedrockAccessStack extends Stack {

  /** The apps will run as ECS tasks, like Mission Control's web app. */
  private static final String ECS_TASKS = "ecs-tasks.amazonaws.com";

  /**
   * Creates the stack.
   *
   * @param model which model may be called, from the {@code rainmaker:bedrock} context
   */
  public BedrockAccessStack(
      Construct scope, String id, StackProps props, BedrockModelAccess model) {
    super(scope, id, props);

    Role role =
        Role.Builder.create(this, "G2ContentAppRole")
            .roleName("rainmaker-g2-content-app")
            .description("g2-content-app: classify articles with Claude through Bedrock (#161)")
            // aws:SourceAccount: only ECS tasks in this account, never another account's tasks.
            .assumedBy(
                new ServicePrincipal(ECS_TASKS)
                    .withConditions(
                        Map.of("StringEquals", Map.of("aws:SourceAccount", getAccount()))))
            .inlinePolicies(
                Map.of(
                    "invoke-claude-through-eu-profile",
                    PolicyDocument.Builder.create()
                        .statements(model.invokeStatements(getAccount(), getRegion()))
                        .build()))
            .build();
    // Kept if the stack is deleted, like the deploy role in GithubDeployAccessStack.
    role.applyRemovalPolicy(RemovalPolicy.RETAIN);
  }
}
