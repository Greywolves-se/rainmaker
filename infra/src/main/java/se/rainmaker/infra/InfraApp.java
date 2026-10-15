package se.rainmaker.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/** Entry point for the CDK CLI ({@code cdk synth}, {@code cdk diff}, {@code cdk deploy}). */
public final class InfraApp {

  private static final String STAGING_ACCOUNT = "659947636280";
  private static final String REGION = "eu-north-1";

  private InfraApp() {}

  /** Defines the stacks and writes their CloudFormation templates to {@code cdk.out/}. */
  public static void main(String[] args) {
    App app = new App();
    StackProps staging =
        StackProps.builder()
            .env(Environment.builder().account(STAGING_ACCOUNT).region(REGION).build())
            .build();
    new GithubDeployAccessStack(app, "GithubDeployAccess-staging", staging, "staging");
    app.synth();
  }
}
