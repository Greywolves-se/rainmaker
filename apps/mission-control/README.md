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

## Work
All issues have the label `mission-control` and the milestone "Mission Control V1". Epics: #1 Foundation, #2 Data collection, #3 Dashboard, #4 Summaries and onboarding, #5 Quality and docs. Later work: #32.
