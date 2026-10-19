# Rainmaker infrastructure

AWS CDK app (Java) that defines Rainmaker's AWS resources. CLAUDE.md: all AWS resources
are created with CDK through the pipeline, never by hand in the console.

- One stack per environment. Today only **staging** (account `659947636280`, `eu-north-1`).
- Package `se.rainmaker.infra`. The module inherits Spotless, Checkstyle and JaCoCo from the
  root POM (#39).

## Stacks

| Stack | What | Issue |
|---|---|---|
| `GithubDeployAccess-staging` | GitHub OIDC provider and the `github-actions-rainmaker` role with three inline policies: `ecr-push-mission-control` (push the images), `ecs-deploy-mission-control-web` (roll out the web service) and `read-mission-control-secrets` (read the two collector secrets below). Only jobs in the GitHub environment `staging` of `Greywolves-se/rainmaker` can assume the role. `docker.yml` uses it. | #61, #82, #112 |

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

## Collector secrets (#112)

The collector's Google Drive and OpenAI sources (#113, #115) need two secrets. They live in
**AWS Secrets Manager** in the staging account (`eu-north-1`). Only their **names** are in code;
nobody puts a value in the repository, an issue, a PR, a log or a chat.

| Secret name | Contents | Environment variable in the collector step |
|---|---|---|
| `mission-control/google-drive-service-account` | JSON key of a read-only Google service account (scope `drive.metadata.readonly`, Viewer on the shared project folders) | `MC_GOOGLE_SERVICE_ACCOUNT_JSON` |
| `mission-control/openai-api-key` | API key of the team's OpenAI organisation, which has a monthly spending limit | `OPENAI_API_KEY` |

- **Access:** the `read-mission-control-secrets` policy lets the deploy role read exactly these two
  secrets (`secretsmanager:GetSecretValue`), nothing else. `docker.yml` reads them inside the
  "Collect the snapshot" step only and masks them in the log. A secret that is not set up yet is
  skipped with a notice; the collector then runs without it.
- **Who sets them up:** an admin of the staging account creates both secrets by hand with exactly
  these names (`aws secretsmanager create-secret --name …`, or the console), and pastes the value
  directly into Secrets Manager. The Google service account is created by a Google Workspace admin;
  the OpenAI key by whoever owns the team's OpenAI billing.
- **Rotate:** create a new key (Google Cloud console or OpenAI dashboard), store it with
  `aws secretsmanager put-secret-value --secret-id <name> --secret-string file://<file>` (or in the
  console), check the next collector run, then delete the old key at the source. No code change is
  needed, because the name stays the same.
- **Revoke:** delete the key at the source (Google or OpenAI). The collector then reports that source
  as `error` and the rest of the snapshot is unaffected.
