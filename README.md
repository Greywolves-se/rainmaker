# Rainmaker

Contextual performance marketplace for digital news publishers. A script on the
publisher's site shows a small, labelled panel with relevant commercial links when a
reader hovers a headline. AI suggests the links, an editor approves them, and every
click is tracked for settlement.

**New here? Read [docs/START_HERE.md](docs/START_HERE.md) first.** Rules for code,
PRs and AI use are in [CLAUDE.md](CLAUDE.md).

## Repository layout

```
rainmaker/
├── pom.xml                  parent POM, lists all Maven modules
├── CLAUDE.md                shared AI and team rules
├── docs/                    START HERE, architecture decision records
├── services/                one Maven module per logical service
│   ├── widget-delivery/         S1
│   ├── tracking-click/          S2
│   ├── publisher-control/       S3
│   ├── advertiser-marketplace/  S4
│   ├── content-ingestion/       S5 (build week 2)
│   ├── content-intent/          S6 (build week 2)
│   ├── matching-ranking/        S7
│   ├── inventory-integration/   S8
│   ├── analytics-qti/           S9
│   └── clearing-house/          S10
├── apps/                    deployable Spring Boot apps
│   ├── g1-reader-app/           S1 + S2
│   ├── g2-content-app/          S5 + S6 + S7
│   ├── g3-publisher-app/        S3 + S4 + S8
│   ├── g4-money-app/            S9 + S10
│   └── mission-control/         internal status app, built first
├── widget/                  rainmaker.js (TypeScript, not Maven)
└── portals/                 React apps (not Maven)
```

Every folder has a README that explains what belongs there.

## Build

Needs JDK 21. Maven itself is downloaded by the wrapper.

```
./mvnw verify        # macOS / Linux / Git Bash
.\mvnw.cmd verify    # Windows cmd / PowerShell
```

## Working together

- Git Flow: `feature/<issue-number>-short-name` from `develop`, pull request back to
  `develop`. `main` is production.
- Every PR needs one approval from another developer and must stay under ~400 changed lines.
- All details: [CLAUDE.md](CLAUDE.md).
