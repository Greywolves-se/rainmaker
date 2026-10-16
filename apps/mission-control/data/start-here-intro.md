## What we are building

Rainmaker is a contextual performance marketplace for digital news publishers. A script tag on the publisher's site shows a small, labelled panel with relevant commercial links when a reader hovers a headline. AI suggests the links, an editor approves them, and every click is tracked for settlement.

- **Milestones:** technically ready in UAT by 18 Dec 2026, external pilot around 7 Jan 2027.
- **Source of truth for the architecture:** *Rainmaker Solution Architecture v0.5* (see Links).
- **Mission Control** (this report comes from it) is the team's internal status app, built first.

## Architecture in short

- Ten logical services in four deployable groups: **G1** reader & clicks (S1 widget delivery, S2 tracking & click), **G2** content & AI (S5 ingestion, S6 content & intent, S7 matching & ranking), **G3** publisher & marketplace (S3, S4, S8), **G4** measurement & money (S9 analytics & QTI, S10 clearing house).
- Java 21 + Spring Boot 4.1, React + TypeScript, PostgreSQL, AWS (eu-north-1) with CDK. One monorepo: `services/`, `apps/`, `widget/`, `portals/`.
- Each service owns its database and is used only through its `api` package. Inside a group: interfaces + Spring events; between groups: REST and EventBridge + SQS.
- **Product rules that must never break:** red articles get no panel; the publisher's page loads even if Rainmaker fails; clicks are idempotent on `click_id`; rank by relevance, price only breaks ties; never write to the publisher's CMS; respect reader consent.

## How we work

- Work from a GitHub issue; branch `feature/<issue>-short-name` from `develop`; PRs under ~400 lines with a summary; a human approves every PR.
- Read `CLAUDE.md` in the repository before using Claude Code; it holds all rules.
