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
`pom.xml` in this folder groups Mission Control's Java modules (today: none; `collector` is added in #7). Each module inside lists this POM (`mission-control`, `../pom.xml`) as its `<parent>`. Build only Mission Control from the repository root:

```
./mvnw -f apps/mission-control/pom.xml verify
```

The web app is built with npm inside `web/`.

## CI
`.github/workflows/mission-control-ci.yml` runs on pull requests and pushes to `develop` and `main` that change `apps/mission-control/**`. The web job (lint, typecheck, tests, build on Node 22) only runs when `web/` or `contract/` changed. A collector job is added once the collector has a `pom.xml` (#7).

## Run with Docker
Needs only Docker; no local Java, Maven or Node. Run from this folder:

```
cp .env.example .env          # fill in MC_GITHUB_TOKEN; .env is git-ignored
docker compose up --build     # dashboard on http://localhost:8080
docker compose run --rm collector   # refresh the snapshot, the web app keeps running
docker compose down           # stop; add -v to also delete the snapshot volume
```

The collector runs once and writes `snapshot.json` to a shared volume; the web container (nginx, port 8080) serves it at `/snapshot.json`. Both containers run as non-root users. `data/` is mounted read-only into the collector.

Images are built from the repository root (`collector/Dockerfile`, `web/Dockerfile`). The workflow `.github/workflows/docker.yml` builds them on pull requests and pushes them to Docker Hub on `develop` and `main` (repository secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`). An image is skipped until its source exists (#7, #8).

## Work
All issues have the label `mission-control` and the milestone "Mission Control V1". Epics: #1 Foundation, #2 Data collection, #3 Dashboard, #4 Summaries and onboarding, #5 Quality and docs. Later work: #32.
