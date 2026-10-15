# Rainmaker infrastructure

AWS CDK app (Java) that defines Rainmaker's AWS resources. CLAUDE.md: all AWS resources
are created with CDK through the pipeline, never by hand in the console.

- One stack per environment. Today only **staging** (account `659947636280`, `eu-north-1`).
- Package `se.rainmaker.infra`. The module inherits Spotless, Checkstyle and JaCoCo from the
  root POM (#39).

## Stacks

| Stack | What | Issue |
|---|---|---|
| `GithubDeployAccess-staging` | GitHub OIDC provider, the `github-actions-rainmaker` role and its `ecr-push-mission-control` policy. Only jobs in the GitHub environment `staging` of `Greywolves-se/rainmaker` can assume the role. `docker.yml` uses it to push images to ECR. | #61 |

ECR repositories and the ECS service follow in #62.

## Build and test (no AWS access needed)

From the repository root:

```
./mvnw -pl infra -am verify
```

The tests turn each stack into a CloudFormation template in memory and check it
(`software.amazon.awscdk.assertions.Template`). The CDK Java library runs on Node.js
internally, so Node.js must be installed (22 or newer).

## Synth, diff and deploy (needs the CDK CLI and AWS access)

Needs the CDK CLI (`npm install -g aws-cdk`) and credentials for the staging account.
Run from `infra/`:

```
cdk synth     # writes the templates to cdk.out/ (git-ignored)
cdk diff      # shows what would change in AWS; paste the output into infra PRs
cdk deploy    # changes AWS; only through the pipeline or with senior approval
```

`cdk.json` starts the app with `../mvnw`, which works in Linux, macOS, CI and WSL. On
Windows, run `cdk` from WSL; plain `cmd.exe` cannot run `../mvnw`.

Never print or commit secret values. Reference secrets by name or ARN (see CLAUDE.md).

## Still to do for #61 (needs admin access to staging)

1. `cdk bootstrap aws://659947636280/eu-north-1` (once; record who ran it and the output in
   the PR).
2. Compare the stack with the resources created by hand in #59 (ECR actions in the policy,
   OIDC thumbprints), then `cdk import` them into `GithubDeployAccess-staging`.
3. `cdk diff` must show no changes, and `docker.yml` must still push to ECR.
