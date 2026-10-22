package se.rainmaker.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.sso.CfnPermissionSet;
import software.constructs.Construct;

/**
 * The IAM Identity Center permission set {@code RainmakerBedrockDeveloper} (#161): developers sign
 * in with {@code aws sso login} and get exactly the G2 role's Bedrock access, from the same {@link
 * BedrockModelAccess}, so the two can never drift apart. No access keys exist.
 *
 * <p>The stack assigns nobody. An Identity Center admin assigns the permission set to the
 * developers (or their group) for the dev account in the console, so no names or e-mail addresses
 * are in the code.
 *
 * <p>It is deployed to the account that holds the Identity Center instance (the organisation's
 * management account), while its policy names the dev account, where Bedrock is called.
 */
public class DeveloperAccessStack extends Stack {

  /**
   * Creates the stack.
   *
   * @param model which model may be called, from the {@code rainmaker:bedrock} context
   * @param bedrockAccount the dev account, where the developers call Bedrock
   * @param bedrockRegion the region they send their requests to
   * @param identityCenterInstanceArn the Identity Center instance, passed with {@code -c
   *     rainmaker:sso-instance-arn=<arn>}
   */
  public DeveloperAccessStack(
      Construct scope,
      String id,
      StackProps props,
      BedrockModelAccess model,
      String bedrockAccount,
      String bedrockRegion,
      String identityCenterInstanceArn) {
    super(scope, id, props);

    CfnPermissionSet.Builder.create(this, "BedrockDeveloper")
        .name("RainmakerBedrockDeveloper")
        .description("Call Claude Haiku through the EU Bedrock profile, as g2-content-app (#161)")
        .instanceArn(identityCenterInstanceArn)
        // A working day; the developer signs in again with aws sso login after that.
        .sessionDuration("PT8H")
        .inlinePolicy(
            PolicyDocument.Builder.create()
                .statements(model.invokeStatements(bedrockAccount, bedrockRegion))
                .build()
                .toJSON())
        .build();
  }
}
