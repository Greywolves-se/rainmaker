# Mission Control

The team's working environment and shared project memory: status, decisions, risks, links and the START HERE onboarding report (architecture v0.5, section 10.6). The first part of Rainmaker to be built.

## How V1 works
A Java collector reads GitHub and YAML data files, writes one JSON snapshot, and a React dashboard shows it. V1 runs locally only. See the decision in issue #6.

| Folder | Contents | Issue |
|---|---|---|
| `collector/` | Java 21 + Spring Boot collector (Maven module) | #7 |
| `web/` | React + TypeScript + Vite dashboard (not Maven) | #8 |
| `contract/` | Snapshot JSON Schema and a fictional sample | #10 |
| `data/` | Decisions, risks, questions, links, change log (YAML) | #15, #16 |
| `docs/` | Guide for adding a data source | #31 |
| `out/` | Generated snapshot and reports – git-ignored, never commit | |

## Build
`pom.xml` in this folder groups Mission Control's Java modules (today: `anonymizer`, `collector` and `navigator-api`). Each module inside lists this POM (`mission-control`, `../pom.xml`) as its `<parent>`. Build only Mission Control from the repository root:

```
./mvnw -f apps/mission-control/pom.xml verify
```

The web app is built with npm inside `web/`.

**Anonymizer (#115, ADR 0002):** `anonymizer/` is a small plain-Java module with no dependencies. It replaces people's names and logins with neutral labels ("Person A", "Person B" …) before text goes to an AI model, and puts them back in the answer. The collector (#115) and the Ask Navigator backend (ADR 0003, #117) both use it, so there is one implementation. Only the names it is given are hidden; see the class Javadoc for the rules and the known limit. It has the 80 % coverage gate.

### Run the collector
The collector is a command-line app (Spring Boot, no web server): it runs once and exits with code 0 on success. Every run logs JSON lines that share one `correlation_id`. From the repository root, after the build above:

```
java -jar apps/mission-control/collector/target/collector-0.1.0-SNAPSHOT.jar
```

**OpenAI key (#112) and Google login (#120):** the OpenAI part (#115) reads `OPENAI_API_KEY`; in staging it comes from AWS Secrets Manager (see `infra/README.md`, "Collector credentials"), and locally you use your own key in your shell or in `.env` (git-ignored), never the staging one. The Google Drive source needs **no key**: it uses Google's standard login, through Workload Identity Federation in staging and with the read-only service account impersonated locally (see "Google Drive" below).

**Projects (#99):** the collector writes one snapshot per project: **Rainmaker** (the product) and **Mission Control** (this app). For each project, a run asks every enabled source for that project's data, in id order, and writes `apps/mission-control/out/<project>/snapshot.json` and `start-here.md` (git-ignored; never commit them). `out/projects.json` lists the projects that have a snapshot (id, and the name from that snapshot), so the web app needs no hard-coded list; its contract is `contract/projects.schema.json`. A project removed from the settings drops out of `projects.json`, but its `out/<id>/` folder stays until you delete it (harmless; `out/` is git-ignored and rebuilt in CI). Each file is replaced in one step, so a reader never sees half a snapshot.
- **A source that fails** is listed with `status: error` and a short message, and adds nothing; the other sources are not affected.
- **A project that fails** keeps its previous files and the other project is still written. The run then exits with an error, so CI does not deploy.

**Which project an item belongs to:** GitHub issues and pull requests **labelled `mission-control`** belong to Mission Control; everything else belongs to Rainmaker. **Label Mission Control pull requests too**, or they show up under Rainmaker. Data files live in `data/<project>/`.

Settings live in `collector/src/main/resources/application.properties` and can be overridden on the command line, e.g. `--mission-control.collector.output-folder=/tmp/out`:

| Setting | Default |
|---|---|
| `mission-control.collector.output-folder` | `apps/mission-control/out` (relative to the working directory) |
| `mission-control.collector.projects[n].id`, `.name` | `rainmaker` "Rainmaker", `mission-control` "Mission Control". The id names the output and data folders; the name is used when `project.yaml` gives none |
| `mission-control.collector.projects[n].with-label`, `.without-label` | GitHub label rule: only items with / without that label belong to the project |
| `mission-control.collector.sources.<id>.enabled` | `true`; set `false` to switch a source off (it is then listed as `disabled`) |

**Data files (#15):** decisions, risks, questions, links, the change log and the project's key dates live as YAML in `data/<project>/` (e.g. `data/rainmaker/decisions.yaml`). A missing file means no items. Each file starts with a comment that explains its fields. Edit them in a pull request (the GitHub web editor is fine); a test checks the real files in CI. It names the file, line and field of every mistake, so a bad edit fails the PR instead of the dashboard. Each item links back to its line on GitHub.

**GitHub (#12):** issues from the repositories in `mission-control.collector.github.repositories` (default `Greywolves-se/rainmaker`) become work items with the id `gh-<repo>-<number>`. Pull requests are left out (#13), and closed issues older than 30 days are dropped.
- **Status rules, in this order:**
  1. closed → `done`;
  2. label `blocked` → `blocked`;
  3. label `status: in progress`, or open with an assignee → `in_progress`;
  4. otherwise → `todo`.

  Labels are compared without case.
- **Sprint:** chosen among the open milestones that hold at least one of the project's issues: the one due today or later with the nearest due date. If every open milestone is overdue, the most recently overdue one is used. Its start date is the day the milestone was created, and its goal is the first line of the description. **Milestones without a due date are not sprints**, so give each sprint milestone a due date, or the sprint card stays empty. (Today the only milestone, "Mission Control V1", has none.)
- **Events:** issues closed in the last 7 days also appear as events.
- **Token:** read from `MC_GITHUB_TOKEN`, else `GITHUB_TOKEN`; it is never logged. Use a fine-grained, read-only token for the listed repositories.
  - **Permissions needed:** **Issues: read**, **Pull requests: read**, **Checks: read** and **Actions: read**, plus Contents and Metadata (read), which the builds source also uses. A missing one gives HTTP 403, and that source shows `error`.
  - **Staging:** the `docker.yml` job grants the same to its built-in token.
  - **Without a token,** the GitHub sources show `error` ("No GitHub token") and the rest of the run continues.

**GitHub pull requests (#13, source `github-pull-requests`):** open pull requests (drafts included) and those merged in the last 7 days. Merged ones also appear as events.
- **Review:** the latest review of each reviewer counts (a dismissed review cancels it). Any "changes requested" wins, then any approval; otherwise `pending`.
- **Checks:** no check runs → `none`; a failed, timed-out or action-required run → `failing`; else a run still going → `pending`; else `passing`.
- **Waiting time:** how long a PR has waited for review is shown by the dashboard from `createdAt` and `review` (#21).

**GitHub builds and versions (#14, source `github-builds`):**
- **Builds:** the latest run of each of the project's workflows on `main`, `develop` and the newest `release/x.y.z` branch. The workflows are set per project in `mission-control.collector.github.workflows.<project id>` as file names (`mission-control-ci.yml,docker.yml`), because both projects live in one repository. Skipped and neutral runs are passed over. A project without workflows shows the source as `disabled` ("No GitHub workflows for this project"); today that is Rainmaker.
- **Status:** not finished → `running`; failed, timed out, startup failure or action required → `failure`; cancelled → `cancelled`; otherwise `success`.
- **Events:** a failed build on `develop` also appears as an event.
- **Versions** (`releases`, one per environment, derived from Git Flow until real deployments are read): **dev** is the latest commit on `develop` (short SHA); **UAT** is the newest `release/x.y.z` branch; **production** is the latest published GitHub release (drafts and pre-releases are left out). An environment with nothing yet is left out.

**Google Drive (#113, source `google-drive`):** the newest 50 files directly in each project's Drive folder become `documents`: title, kind (document, spreadsheet, presentation, PDF or other), last editor, modified time and link. **Metadata only:** the source never reads a document's content.
- **Events:** files added or changed in the last 7 days also appear as events ("Document added" or "Document updated").
- **Folders:** each project's folder is set in `mission-control.collector.google-drive.folders.<project id>` (Rainmaker's "Project files" folder is prepared but commented out until the Google login works in CI, #112). Subfolders, shortcuts, trashed files and files without a name are left out. A project without a folder shows Google Drive as `disabled` ("No Google Drive folder for this project"), never as synced.
- **Login (#120):** a Google **service account** with the read-only scope `drive.metadata.readonly`, reached through **Google's standard login** (Application Default Credentials). No key exists or is stored anywhere. Share each folder with the service account as **Viewer**.
  - **CI:** Workload Identity Federation. The `google-github-actions/auth` step in `docker.yml` (#112, #124) swaps the GitHub run's identity for a short-lived Google token as the service account. It is skipped until the `staging` environment has its two variables (see `infra/README.md`, "Collector credentials").
  - **Locally:** `gcloud auth application-default login --impersonate-service-account=<service account e-mail>` (needs the role "Service Account Token Creator" on the service account). **A plain `gcloud auth application-default login` is refused:** that login ignores the read-only scope and would use your own Drive access, so the source shows an error asking for `--impersonate-service-account`.
  - **With `docker compose`:** mount the login file read-only and point to it, e.g. in a `docker-compose.override.yml` (git-ignored): `volumes: ["${APPDATA}/gcloud/application_default_credentials.json:/gcloud/adc.json:ro"]` (on macOS and Linux `~/.config/gcloud/…`) and `environment: GOOGLE_APPLICATION_CREDENTIALS: /gcloud/adc.json`. **Treat that file like a password:** it holds your personal gcloud refresh token. Never copy it into the repository or an image.
  - **No login at all:** the library first looks for a Google Cloud metadata server and waits for it to time out. Setting `NO_GCE_CHECK=true` (already set for the collector in `docker-compose.yml`) skips that.
  - **Without a login,** a project with a folder shows the source as `error` ("No Google login …"). Tokens are never logged.
- **Errors:** timeouts, rate limits (HTTP 429, or 403 with a rate-limit reason) and server errors are retried with back-off. A rejected token (401) is renewed once. Any other 403 usually means the folder isn't shared with the service account.

**Navigator AI text (#115, ADR 0002, source `navigator-ai`):** after all sources ran, the collector asks OpenAI once per page (Overview, Development, Sources) and project for a summary, deviations and suggested next steps, in **Swedish and English from one call**. It stores the result in the snapshot's `navigator` section.
- **No names leave:** every person in the snapshot (assignees, authors, owners, also each name inside an owner list, and last editors) is replaced with "Person A", "Person B" … by the `anonymizer` module before the call, and put back in the answer. Only the page's data is sent, never URLs. A name that appears only in free text, and in no person field, is not hidden.
- **Model and settings:** `mission-control.collector.navigator.*` in `application.properties`. The default model is `gpt-6-luna`; change it there, no code change needed. The API is OpenAI's Responses API with a JSON Schema for the answer (structured output).
- **Off until switched on:** `MC_NAVIGATOR_ENABLED=true` and an API key in `OPENAI_API_KEY` (Peter's OpenAI account; AWS Secrets Manager in CI, #112). Switched off, the source shows `disabled`; without a key, `error`.
- **Never stops the snapshot:** a page that fails (a refusal, an answer that isn't the agreed JSON or not JSON at all) gets no text and the others are kept. A failure every call would hit (a rejected key, "OpenAI quota used up; check billing", or OpenAI not answering after the retries) stops the rest of the run, so a hanging OpenAI costs one page's retries (about 2 minutes), not every page; if every page fails, `navigator` is `null` and the source shows `error` with a safe message. Answers are cut to the contract's limits. The key is never logged.

**START HERE report (#25):** every run also writes `out/<project>/start-here.md` next to each snapshot. It's a Markdown briefing for a new team member or an AI assistant:
- the hand-written intro from `data/<project>/start-here-intro.md` (product goal, architecture in short, how we work);
- then generated sections: status (sprint, key dates, sources with problems), next work, pull requests waiting for review, decisions, red and yellow risks, open questions, latest release and links.

Every list is sorted and capped, so the report stays about two pages. The same snapshot always gives the same file. Change the intro or the data files, never the report.

**Adding a source:** implement `SourceCollector` as a Spring bean (see the Javadoc). Its `collect` method is called once per project and adds that project's items to the `SnapshotBuilder` it is given. A source that only covers some projects overrides `notSetUpFor`, so it is listed as `disabled` with a reason for the others instead of as synced. If it cannot deliver, it throws `SourceException` with a message that is safe to show. The GitHub (#12) and data-file (#15) sources are the first real ones.

## CI
`.github/workflows/mission-control-ci.yml` runs on every pull request into `develop` and `main` (so its checks can be required before merging, #109), and on pushes to them that change `apps/mission-control/**` or `infra/**`. Jobs a change does not need are skipped, which counts as passing. The web job (lint, typecheck, tests, build on Node 22) only runs when `web/` or `contract/` changed. The collector job (format, Checkstyle, tests for all of Mission Control's Java) runs when `anonymizer/`, `collector/`, `contract/`, `data/` or the Maven setup changed. The infra job (CDK synth and tests, JDK 21 + Node 22, no AWS access, #125) runs when `infra/` or the Maven setup changed; see `infra/README.md`.

## Run with Docker
Needs only Docker; no local Java, Maven or Node. Run from this folder:

```
cp .env.example .env          # fill in MC_GITHUB_TOKEN; .env is git-ignored
docker compose up --build     # dashboard on http://localhost:8080
docker compose run --rm collector   # refresh the snapshot, the web app keeps running
docker compose down           # stop; add -v to also delete the snapshot volume
```

The collector runs once and writes `projects.json` and one folder per project to a shared volume; the web container (nginx, port 8080) serves them at `/projects.json` and `/<project>/…` (#100). Both containers run as non-root users. `data/` is mounted read-only into the collector.

Images are built from the repository root (`collector/Dockerfile`, `web/Dockerfile`). The workflow `.github/workflows/docker.yml` builds them on pull requests and pushes them to Docker Hub on `develop` and `main` (repository secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`). An image is skipped until its source exists: the web image is built, the collector image is skipped until the collector exists (#7).

## Work
All issues have the label `mission-control` and the milestone "Mission Control V1". Epics: #1 Foundation, #2 Data collection, #3 Dashboard, #4 Summaries and onboarding, #5 Quality and docs. Later work: #32.
