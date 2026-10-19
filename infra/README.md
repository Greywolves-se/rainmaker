# Rainmaker infrastructure

AWS CDK app (Java) that defines Rainmaker's AWS resources. CLAUDE.md: all AWS resources
are created with CDK through the pipeline, never by hand in the console.

- One stack per environment. Today only **staging** (account `659947636280`, `eu-north-1`).
- Package `se.rainmaker.infra`. The module inherits Spotless, Checkstyle and JaCoCo from the
  root POM (#39).

## Stacks

| Stack | What | Issue |
|---|---|---|
| `GithubDeployAccess-staging` | GitHub OIDC provider and the `github-actions-rainmaker` role with three inline policies: `ecr-push-mission-control` (push the images), `ecs-deploy-mission-control-web` (roll out the web service) and `read-mission-control-secrets` (read the collector's OpenAI key, see below). Only jobs in the GitHub environment `staging` of `Greywolves-se/rainmaker` can assume the role. `docker.yml` uses it. | #61, #82, #112 |

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

## Collector credentials (#112)

The collector's OpenAI and Google Drive sources (#115, #113) need credentials. Only **names and
identifiers** are in code; nobody puts a key in the repository, an issue, a PR, a log or a chat.

### OpenAI key (secret in AWS Secrets Manager)

| Secret name | Contents | Environment variable in the collector |
|---|---|---|
| `mission-control/openai-api-key` | API key of the team's OpenAI organisation, which has a monthly spending limit | `OPENAI_API_KEY` |

- **Access:** the `read-mission-control-secrets` policy lets the deploy role read exactly this
  secret (`secretsmanager:GetSecretValue`), nothing else. In `docker.yml` the collector is built
  before the job has any AWS credentials. "Collect the snapshot" then reads the key, masks it in
  the log, removes the AWS credentials and passes the key to the collector process only. A
  secret that does not exist yet gives a notice; any other error (for example
  `AccessDeniedException`) gives a warning with the AWS error code. Either way the collector runs
  without it.
- **Who sets it up:** an admin of the staging account creates the secret by hand with exactly this
  name and pastes the key directly into Secrets Manager. The key comes from whoever owns the
  team's OpenAI billing.
- **Encryption key:** use the default `aws/secretsmanager` KMS key. A customer-managed key would
  also need `kms:Decrypt` in the policy.
- **Rotate:** create a new key in the OpenAI dashboard, store it with
  `aws secretsmanager put-secret-value --secret-id mission-control/openai-api-key --secret-string file://<file>`
  (or in the console), check the next collector run, then revoke the old key. No code change is
  needed.
- **Revoke:** revoke the key in OpenAI. The collector then reports that source as `error` and the
  rest of the snapshot is unaffected.

### Google Drive (Workload Identity Federation, no key)

GitHub Actions signs in to Google with its OIDC token, the same way it signs in to AWS, and acts
as the read-only service account `mission-control-drive-reader` in the Google Cloud project
`mission-control-512109`. **No Google key exists**, which also follows the `greywolves.se`
organisation rule that blocks service-account keys.

- **In `docker.yml`:** `google-github-actions/auth` runs before the collector and sets
  `GOOGLE_APPLICATION_CREDENTIALS` to a short-lived credentials file (`gha-creds-*.json`, git- and
  docker-ignored). The collector uses it through Application Default Credentials with the scope
  `drive.metadata.readonly` (#120). The step is skipped until the `staging` environment has the
  two variables below.
- **GitHub variables** (environment `staging`, not secrets): `GCP_WORKLOAD_IDENTITY_PROVIDER`
  (`projects/<project number>/locations/global/workloadIdentityPools/github/providers/rainmaker`)
  and `GCP_SERVICE_ACCOUNT` (the service account's email).
- **One-time setup by a Google Cloud admin** (project `mission-control-512109`):
  1. Create a workload identity pool `github` and an OIDC provider `rainmaker` with issuer
     `https://token.actions.githubusercontent.com`, attribute mapping
     `google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.environment=assertion.environment`
     and the **condition**
     `assertion.repository == 'Greywolves-se/rainmaker' && assertion.environment == 'staging'`.
     Without the condition, any GitHub repository could get a token.
  2. Grant `roles/iam.workloadIdentityUser` on the service account to
     `principalSet://iam.googleapis.com/projects/<project number>/locations/global/workloadIdentityPools/github/attribute.repository/Greywolves-se/rainmaker`.
  3. The Drive API is enabled, and the project folder ("Google Drive – Project files" in
     `data/rainmaker/links.yaml`) is shared with the service account as **Viewer**, by the
     folder's owner.
- **Revoke:** remove the `workloadIdentityUser` binding or disable the provider. Unsharing the
  folder removes access to its files.
- **Locally:** sign in with `gcloud auth application-default login` and impersonate the service
  account (needs "Service Account Token Creator"); documented in #120.
