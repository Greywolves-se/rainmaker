# Rainmaker infrastructure

AWS CDK app (Java) that defines Rainmaker's AWS resources. CLAUDE.md: all AWS resources
are created with CDK through the pipeline, never by hand in the console.

- One stack per environment: **staging** (account `659947636280`) and **dev** (the member account
  `rainmaker-dev`, #161), both in `eu-north-1` (Stockholm). Accounts and regions are context in
  `cdk.json`; the dev account id is not committed (see "Dev account and Bedrock" below).
- Package `se.rainmaker.infra`. The module inherits Spotless, Checkstyle and JaCoCo from the
  root POM (#39).

## Stacks

| Stack | What | Issue |
|---|---|---|
| `GithubDeployAccess-staging` | GitHub OIDC provider and the `github-actions-rainmaker` role with three inline policies: `ecr-push-mission-control` (push the images), `ecs-deploy-mission-control-web` (roll out the web service) and `read-mission-control-secrets` (read the collector's OpenAI key, see below). Only jobs in the GitHub environment `staging` of `Greywolves-se/rainmaker` can assume the role. `docker.yml` uses it. | #61, #82, #112 |

| `BedrockAccess-dev` | The `rainmaker-g2-content-app` role: ECS tasks in the dev account may call Claude Haiku 5.5 through the EU inference profile, and nothing else. See "Dev account and Bedrock" below. | #161 |

ECR repositories and the ECS service follow in #62.

## Build and test (no AWS access needed)

From the repository root:

```
./mvnw -pl infra -am verify
```

The tests turn each stack into a CloudFormation template in memory and check it
(`software.amazon.awscdk.assertions.Template`). The CDK Java library runs on Node.js
internally, so Node.js must be installed (22 or newer).

**CI (#125):** the "Infra (CDK synth and tests)" job in `.github/workflows/mission-control-ci.yml`
runs the same command, with JDK 21 and Node.js 22, on every pull request that changes `infra/`,
the root `pom.xml`, `mvnw`, `.mvn/` or the workflow file. Other pull requests skip it, and a
skipped job counts as passing, so the check can be required. The job has no AWS credentials, so
`cdk diff` stays a manual step in every infra PR.

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

## Staging account status (#82)

The staging account is **bootstrapped**, and `GithubDeployAccess-staging` **manages** the GitHub
OIDC provider and the `github-actions-rainmaker` role. Done by Maruf on 19 Oct 2026 in AWS
CloudShell; the outputs are in #82. The change logs record each step with a rollback plan: the
bootstrap and the import, which the whole repository relies on, in
[`../apps/mission-control/data/rainmaker/changelog.yaml`](../apps/mission-control/data/rainmaker/changelog.yaml)
(C-1, C-2); the collector's secrets policy in
[`../apps/mission-control/data/mission-control/changelog.yaml`](../apps/mission-control/data/mission-control/changelog.yaml)
(C-1).

1. `cdk bootstrap aws://659947636280/eu-north-1` created the `CDKToolkit` stack.
2. `cdk import` adopted the provider and the role **unchanged**, using the code that matched AWS
   at the time (commit `822a112`, two inline policies). Importing with newer code would have made
   CloudFormation believe that policies existed which did not.
3. `cdk diff` showed no resource changes, and drift detection showed both resources `IN_SYNC`.
4. `cdk deploy` from `develop` (`a88422e`, after #124) then added `read-mission-control-secrets`,
   which allows only `mission-control/openai-api-key`; no Google secret was ever deployed.

**From now on** a change to this stack is an ordinary change: a PR with the code, then someone
with access runs `cdk diff GithubDeployAccess-staging` (output in the PR) and `cdk deploy`.
Both resources keep `DeletionPolicy: Retain`, so deleting the stack never deletes them, nor
their inline policies.

**Node.js:** run the CDK CLI with Node.js 22 or newer. AWS CloudShell came with Node.js 20, which
is end of life since April 2026; it worked with a warning on 19 Oct, but may stop working when
CDK drops support. Install Node.js 22 in the CloudShell session first (for example with `nvm`, if
available; check `node --version` afterwards).

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
     `google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.environment=assertion.environment`
     and the **condition**
     `assertion.repository_owner_id == '341957527' && assertion.repository_id == '1416917981' && assertion.environment == 'staging'`.
     Without the condition, any GitHub repository could get a token. The numeric ids (owner
     `Greywolves-se` = 341957527, repository = 1416917981, the same as in the AWS trust policy)
     never change, so a repository that is renamed or transferred, or a new one that takes over
     the name, cannot match.
  2. Grant `roles/iam.workloadIdentityUser` on the service account to
     `principalSet://iam.googleapis.com/projects/<project number>/locations/global/workloadIdentityPools/github/attribute.repository_id/1416917981`.
  3. The Drive API is enabled, and the project folder ("Google Drive – Project files" in
     `data/rainmaker/links.yaml`) is shared with the service account as **Viewer**, by the
     folder's owner.
- **Revoke:** remove the `workloadIdentityUser` binding or disable the provider. Unsharing the
  folder removes access to its files.
- **Locally:** `gcloud auth application-default login --impersonate-service-account=mission-control-drive-reader@mission-control-512109.iam.gserviceaccount.com`. Without
  the flag the collector refuses the login (#120), since a plain user login cannot be limited to
  the read-only scope. Needs the role "Service Account Token Creator" on the service account;
  documented in #120.

## Dev account and Bedrock (#161)

Rainmaker reaches Claude through **Amazon Bedrock** in the dev account (contract D-8). Nobody
uses an API key: the app gets credentials from its IAM role, and developers from their own SSO
login.

The dev account is the member account `rainmaker-dev` in the GreyWolves AWS Organization, one
account per project and environment. The organisation's management account holds only the
organisation, billing and IAM Identity Center, never workloads (decided 22 Oct 2026, #161).

### Settings for the code (#148)

| Setting | Value |
|---|---|
| Region | `eu-north-1` (Stockholm) |
| Model id to call | `eu.anthropic.claude-haiku-5-5` (the EU cross-region inference profile) |
| Credentials | Locally: `AWS_PROFILE` of your SSO profile. In AWS: the task role |

Call the **profile id**, not the base model id `anthropic.claude-haiku-5-5`. The role only
allows the model *through* the profile, which may route a request to any of the six EU regions
in `cdk.json` (`rainmaker:bedrock`). Changing the model is a change to that context block.

### What the G2 role may do

`bedrock:InvokeModel` (which also covers the Converse API) on two kinds of resource:

1. the inference profile
   `arn:aws:bedrock:eu-north-1:<dev account>:inference-profile/eu.anthropic.claude-haiku-5-5`;
2. `arn:aws:bedrock:<region>::foundation-model/anthropic.claude-haiku-5-5` in each of the six EU
   destination regions, **only** when the request comes through that profile (condition
   `bedrock:InferenceProfileArn`). Calling the base model directly is denied.

Only ECS tasks in the same account (`aws:SourceAccount`) can assume the role.
`BedrockAccessStackTest` pins the exact policy.

### Local access for developers

Each developer signs in with their own IAM Identity Center user; no access keys in files. The
permission set with the same narrow access follows in the second #161 PR. Then:

```
aws configure sso                       # once: SSO start URL, region eu-north-1, the permission set
aws sso login --profile rainmaker-dev   # each day; opens the browser
```

Set `AWS_PROFILE=rainmaker-dev` (or pass the profile to the SDK); the AWS SDK picks up the login.

### Synth, diff and deploy for dev

The dev account id is passed on the command line, never committed (the repository is public):

```
cdk diff   -c rainmaker:dev-account=<dev account id> --profile <admin profile> BedrockAccess-dev
cdk deploy -c rainmaker:dev-account=<dev account id> --profile <admin profile> BedrockAccess-dev
```

Without `-c rainmaker:dev-account=...` the dev stacks are left out, and the staging commands
work as before. Before the first deploy the dev account must be bootstrapped once
(`cdk bootstrap aws://<dev account id>/eu-north-1`); see the change log, C-3 and C-4.

### Cost

- **Nothing here costs money by itself.** IAM roles and policies are free. Bedrock is billed per
  call (input and output tokens), and nothing calls it until #148 runs with this role or an SSO
  login. The CDK bootstrap adds an S3 bucket and an ECR repository that cost fractions of a cent
  a month while nearly empty.
- **Quota:** the quota for Claude Haiku 5.5 cross-region tokens per minute is **0** in the dev
  account (21 Oct 2026). Every call is rejected until an increase is requested in Service Quotas.
- **Where to see the cost:** Billing and Cost Management, Cost Explorer, filter *Service* =
  *Amazon Bedrock*. The account-wide budget ($50 a month, alerts at 50 % and 100 %) already
  covers it. A Cost Anomaly Detection alert follows in the second #161 PR; a Bedrock-only budget
  can be added once Bedrock has billing history.
