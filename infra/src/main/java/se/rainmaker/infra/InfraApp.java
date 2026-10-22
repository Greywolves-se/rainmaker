package se.rainmaker.infra;

import java.util.Map;
import java.util.regex.Pattern;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * Entry point for the CDK CLI ({@code cdk synth}, {@code cdk diff}, {@code cdk deploy}).
 *
 * <p>Accounts and regions come from the context in {@code cdk.json}. The dev account is not
 * committed: pass it on the command line with {@code -c rainmaker:dev-account=<account id>}.
 * Without it the dev stacks are left out, so commands for staging work as before.
 */
public final class InfraApp {

  static final String STAGING = "rainmaker:staging";
  static final String DEV = "rainmaker:dev";
  static final String DEV_ACCOUNT = "rainmaker:dev-account";
  static final String SSO_INSTANCE_ARN = "rainmaker:sso-instance-arn";
  static final String SSO_ACCOUNT = "rainmaker:sso-account";

  private static final Pattern ACCOUNT_ID = Pattern.compile("^\\d{12}$");
  private static final Pattern SSO_INSTANCE =
      Pattern.compile("^arn:aws:sso:::instance/ssoins-[0-9a-zA-Z]+$");

  private InfraApp() {}

  /** Defines the stacks and writes their CloudFormation templates to {@code cdk.out/}. */
  public static void main(String[] args) {
    App app = new App();
    defineStacks(app);
    app.synth();
  }

  /** Adds every stack to the app. */
  static void defineStacks(App app) {
    Map<?, ?> staging = ContextValues.requireObject(app, STAGING);
    new GithubDeployAccessStack(
        app,
        "GithubDeployAccess-staging",
        stackProps(
            ContextValues.requireText(staging, STAGING, "account"),
            ContextValues.requireText(staging, STAGING, "region")),
        "staging");

    Object devAccount = app.getNode().tryGetContext(DEV_ACCOUNT);
    if (devAccount == null) {
      System.err.println(
          "Dev stacks left out: add -c " + DEV_ACCOUNT + "=<account id> to include them.");
      return;
    }
    if (!(devAccount instanceof String account) || !ACCOUNT_ID.matcher(account).matches()) {
      throw new IllegalArgumentException(
          "CDK context \"" + DEV_ACCOUNT + "\" must be a 12-digit AWS account id");
    }
    Map<?, ?> dev = ContextValues.requireObject(app, DEV);
    String devRegion = ContextValues.requireText(dev, DEV, "region");
    StackProps devProps = stackProps(account, devRegion);
    BedrockModelAccess model = BedrockModelAccess.fromContext(app);
    new BedrockAccessStack(app, "BedrockAccess-dev", devProps, model);
    new CostGuardrailsStack(
        app, "CostGuardrails-dev", devProps, CostAlertSettings.fromContext(app));

    // The permission set lives where IAM Identity Center is (the management account), and grants
    // Bedrock in the dev account. Without both values the stack is left out, so the other dev
    // stacks still work.
    Object instanceArn = app.getNode().tryGetContext(SSO_INSTANCE_ARN);
    Object ssoAccount = app.getNode().tryGetContext(SSO_ACCOUNT);
    if (instanceArn == null || ssoAccount == null) {
      System.err.println(
          "DeveloperAccess-dev left out: add -c "
              + SSO_INSTANCE_ARN
              + "=<arn> and -c "
              + SSO_ACCOUNT
              + "=<account id> to include it.");
      return;
    }
    if (!(instanceArn instanceof String arn) || !SSO_INSTANCE.matcher(arn).matches()) {
      throw new IllegalArgumentException(
          "CDK context \"" + SSO_INSTANCE_ARN + "\" must be an Identity Center instance ARN");
    }
    if (!(ssoAccount instanceof String identityCenterAccount)
        || !ACCOUNT_ID.matcher(identityCenterAccount).matches()) {
      throw new IllegalArgumentException(
          "CDK context \"" + SSO_ACCOUNT + "\" must be a 12-digit AWS account id");
    }
    new DeveloperAccessStack(
        app,
        "DeveloperAccess-dev",
        stackProps(identityCenterAccount, devRegion),
        model,
        account,
        devRegion,
        arn);
  }

  private static StackProps stackProps(String account, String region) {
    return StackProps.builder()
        .env(Environment.builder().account(account).region(region).build())
        .build();
  }
}
