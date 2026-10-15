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

A run asks every enabled source for its data, in id order, and writes one snapshot to `apps/mission-control/out/snapshot.json` (git-ignored; never commit it). The file is replaced in one step, so a reader never sees half a snapshot. A source that fails is listed with `status: error` and a short message, and adds nothing; the other sources are not affected.

Settings live in `collector/src/main/resources/application.properties` and can be overridden on the command line, e.g. `--mission-control.collector.output=/tmp/snapshot.json`:

| Setting | Default |
|---|---|
| `mission-control.collector.output` | `apps/mission-control/out/snapshot.json` (relative to the working directory) |
| `mission-control.collector.project-name` | `Rainmaker` (used until a source provides the project) |
| `mission-control.collector.sources.<id>.enabled` | `true`; set `false` to switch a source off (it is then listed as `disabled`) |

**Data files (#15):** decisions, risks, questions, links, the change log and the project's key dates live as YAML in `data/`. Each file starts with a comment that explains its fields. Edit them in a pull request (the GitHub web editor is fine); a test checks the real files in CI. It names the file, line and field of every mistake, so a bad edit fails the PR instead of the dashboard. Each item links back to its line on GitHub.

**Adding a source:** implement `SourceCollector` as a Spring bean (see the Javadoc). Its `collect` method adds items to the `SnapshotBuilder` it is given. If it cannot deliver, it throws `SourceException` with a message that is safe to show. The GitHub (#12) and data-file (#15) sources are the first real ones.

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
