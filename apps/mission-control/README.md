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
`pom.xml` in this folder groups Mission Control's Java modules (today: `collector`). Each module inside lists this POM (`mission-control`, `../pom.xml`) as its `<parent>`. Build only Mission Control from the repository root:

```
./mvnw -f apps/mission-control/pom.xml verify
```

The web app is built with npm inside `web/`.

### Run the collector
The collector is a command-line app (Spring Boot, no web server): it runs once and exits with code 0 on success. Every run logs JSON lines that share one `correlation_id`. From the repository root, after the build above:

```
java -jar apps/mission-control/collector/target/collector-0.1.0-SNAPSHOT.jar
```

**Projects (#99):** the collector writes one snapshot per project: **Rainmaker** (the product) and **Mission Control** (this app). For each project, a run asks every enabled source for that project's data, in id order, and writes `apps/mission-control/out/<project>/snapshot.json` and `start-here.md` (git-ignored; never commit them). Each file is replaced in one step, so a reader never sees half a snapshot.
- **A source that fails** is listed with `status: error` and a short message, and adds nothing; the other sources are not affected.
- **A project that fails** keeps its previous files and the other project is still written. The run then exits with an error, so CI does not deploy.
- **Transition:** until the web app reads per-project files (#100), Mission Control's snapshot is also written to `out/snapshot.json`.

**Which project an item belongs to:** GitHub issues and pull requests **labelled `mission-control`** belong to Mission Control; everything else belongs to Rainmaker. **Label Mission Control pull requests too**, or they show up under Rainmaker. Data files live in `data/<project>/`.

Settings live in `collector/src/main/resources/application.properties` and can be overridden on the command line, e.g. `--mission-control.collector.output-folder=/tmp/out`:

| Setting | Default |
|---|---|
| `mission-control.collector.output-folder` | `apps/mission-control/out` (relative to the working directory) |
| `mission-control.collector.projects[n].id`, `.name` | `rainmaker` "Rainmaker", `mission-control` "Mission Control". The id names the output and data folders; the name is used when `project.yaml` gives none |
| `mission-control.collector.projects[n].with-label`, `.without-label` | GitHub label rule: only items with / without that label belong to the project |
| `mission-control.collector.legacy-snapshot-project` | `mission-control`; also written as `out/snapshot.json` until #100 |
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
  - **Permissions needed:** **Issues: read**, **Pull requests: read** and **Checks: read**, plus Metadata, which is always included. A missing one gives HTTP 403, and that source shows `error`.
  - **Staging:** the `docker.yml` job grants the same three to its built-in token.
  - **Without a token,** the GitHub sources show `error` ("No GitHub token") and the rest of the run continues.

**GitHub pull requests (#13, source `github-pull-requests`):** open pull requests (drafts included) and those merged in the last 7 days. Merged ones also appear as events.
- **Review:** the latest review of each reviewer counts (a dismissed review cancels it). Any "changes requested" wins, then any approval; otherwise `pending`.
- **Checks:** no check runs → `none`; a failed, timed-out or action-required run → `failing`; else a run still going → `pending`; else `passing`.
- **Waiting time:** how long a PR has waited for review is shown by the dashboard from `createdAt` and `review` (#21).

**START HERE report (#25):** every run also writes `out/<project>/start-here.md` next to each snapshot. It's a Markdown briefing for a new team member or an AI assistant:
- the hand-written intro from `data/<project>/start-here-intro.md` (product goal, architecture in short, how we work);
- then generated sections: status (sprint, key dates, sources with problems), next work, pull requests waiting for review, decisions, red and yellow risks, open questions, latest release and links.

Every list is sorted and capped, so the report stays about two pages. The same snapshot always gives the same file. Change the intro or the data files, never the report.

**Adding a source:** implement `SourceCollector` as a Spring bean (see the Javadoc). Its `collect` method is called once per project and adds that project's items to the `SnapshotBuilder` it is given. If it cannot deliver, it throws `SourceException` with a message that is safe to show. The GitHub (#12) and data-file (#15) sources are the first real ones.

## CI
`.github/workflows/mission-control-ci.yml` runs on pull requests and pushes to `develop` and `main` that change `apps/mission-control/**`. The web job (lint, typecheck, tests, build on Node 22) only runs when `web/` or `contract/` changed. The collector job (format, Checkstyle, tests) runs when `collector/`, `contract/`, `data/` or the Maven setup changed.

## Run with Docker
Needs only Docker; no local Java, Maven or Node. Run from this folder:

```
cp .env.example .env          # fill in MC_GITHUB_TOKEN; .env is git-ignored
docker compose up --build     # dashboard on http://localhost:8080
docker compose run --rm collector   # refresh the snapshot, the web app keeps running
docker compose down           # stop; add -v to also delete the snapshot volume
```

The collector runs once and writes `snapshot.json` to a shared volume; the web container (nginx, port 8080) serves it at `/snapshot.json`. Both containers run as non-root users. `data/` is mounted read-only into the collector.

Images are built from the repository root (`collector/Dockerfile`, `web/Dockerfile`). The workflow `.github/workflows/docker.yml` builds them on pull requests and pushes them to Docker Hub on `develop` and `main` (repository secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`). An image is skipped until its source exists: the web image is built, the collector image is skipped until the collector exists (#7).

## Work
All issues have the label `mission-control` and the milestone "Mission Control V1". Epics: #1 Foundation, #2 Data collection, #3 Dashboard, #4 Summaries and onboarding, #5 Quality and docs. Later work: #32.
