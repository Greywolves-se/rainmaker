# Rainmaker infrastructure

AWS CDK app (Java) that defines Rainmaker's AWS resources. CLAUDE.md: all AWS resources
are created with CDK through the pipeline, never by hand in the console.

- One stack per environment. Today only **staging** (account `659947636280`, `eu-north-1`).
- Package `se.rainmaker.infra`. The module inherits Spotless, Checkstyle and JaCoCo from the
  root POM (#39).

## Stacks

| Stack | What | Issue |
|---|---|---|
| `GithubDeployAccess-staging` | GitHub OIDC provider and the `github-actions-rainmaker` role with two inline policies: `ecr-push-mission-control` (push the images) and `ecs-deploy-mission-control-web` (roll out the web service). Only jobs in the GitHub environment `staging` of `Greywolves-se/rainmaker` can assume the role. `docker.yml` uses it. | #61, #82 |

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

## Importing the existing role (#82, needs admin access to staging)

The stack already matches the provider and role in AWS (compared with `aws iam get-role` and
`get-role-policy` on 15 Oct 2026). Both have `DeletionPolicy: Retain`, which `cdk import`
requires and which keeps them if the stack is ever deleted.

1. `cdk bootstrap aws://659947636280/eu-north-1` (once; the account is not bootstrapped yet).
   Record who ran it and the output in the PR.
2. `cdk import GithubDeployAccess-staging`. Identifiers: the provider ARN
   `arn:aws:iam::659947636280:oidc-provider/token.actions.githubusercontent.com` and the role
   name `github-actions-rainmaker`. The inline policies come in with the role.
3. `cdk diff GithubDeployAccess-staging` must show no changes. CloudFormation does not compare
   the template with the real resources during an import, so also run drift detection
   (`aws cloudformation detect-stack-drift`) and check that nothing has drifted.
4. After the next merge to `develop`, `docker.yml` must still push to ECR and deploy staging.

Never run `cdk deploy` on this stack before steps 1–3 are clean.
