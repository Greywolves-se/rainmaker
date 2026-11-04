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

  private static final Pattern ACCOUNT_ID = Pattern.compile("^\\d{12}$");

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
    // G2 calls Bedrock in this region, so it stays in the EU like the profile's destinations.
    if (!devRegion.startsWith("eu-")) {
      throw new IllegalArgumentException(
          "CDK context \"" + DEV + "\": region must be an EU region: " + devRegion);
    }
    new BedrockAccessStack(
        app,
        "BedrockAccess-dev",
        stackProps(account, devRegion),
        BedrockModelAccess.fromContext(app));
  }

  private static StackProps stackProps(String account, String region) {
    return StackProps.builder()
        .env(Environment.builder().account(account).region(region).build())
        .build();
  }
}
