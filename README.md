# Rainmaker

Contextual performance marketplace for digital news publishers. A script on the
publisher's site shows a small, labelled panel with relevant commercial links when a
reader hovers a headline. AI suggests the links, an editor approves them, and every
click is tracked for settlement.

We start with **Mission Control**, the team's internal status app
([apps/mission-control](apps/mission-control/README.md)). The other parts are added
one at a time.

## Repository layout

```
rainmaker/
├── pom.xml              parent POM, lists all Maven modules
├── docs/                architecture, decisions, START HERE        (empty for now)
├── services/            one Maven module per logical service S1–S10 (empty for now)
├── apps/                deployable apps
│   └── mission-control/     internal status app – built first
├── widget/              rainmaker.js, TypeScript, not Maven         (empty for now)
└── portals/             React apps, not Maven                       (empty for now)
```

The service apps G1–G4 (`apps/g1-reader-app` … `apps/g4-money-app`) are added later.
Empty folders contain only a `.gitkeep`, because Git cannot store empty folders.

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
