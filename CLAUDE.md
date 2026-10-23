# Rainmaker - Claude Code instructions

These instructions apply to every Claude Code session in this repository.
Module-specific rules live in the module's own `CLAUDE.md` and take precedence
for that module.

## Project context
Rainmaker is a contextual performance marketplace for digital news publishers.
A script tag on the publisher's site shows a small, labelled panel with relevant
commercial links when a reader hovers a headline. AI suggests the links, an editor
approves them, and every click is tracked for settlement.

- Before starting a task, read the GitHub issue, the README of the folder you work in,
  and `/docs/START_HERE.md` once it exists (Mission Control will generate it, #25).
  Read issues with `gh issue view <number> --comments`; acceptance criteria are often
  refined in the comments.
- Source of truth for architecture: *Rainmaker Solution Architecture v0.5*.
- Milestones: technically ready in UAT by 18 Dec 2026, external pilot ~7 Jan 2027.

### Repository layout
| Folder | What | Build | Status |
|---|---|---|---|
| `apps/mission-control/` | Internal status app: `collector/` (Java) + `web/` (React) | Maven aggregator + npm | **In progress** (#1–#32) |
| `test-site/` | Fictional news site (Vintrabladet) to test the widget, ingestion and AI against; not part of the product | Maven module | Done; see its `README.md` |
| `services/<name>/` | One logical service each (S1–S10, table below), grouped by `services/pom.xml` | Maven module | S1–S7 created empty (#155); S8–S10 later |
| `apps/g1`–`g4-*-app/` | Deployable Spring Boot apps that assemble services (ports 8081–8083) | Maven module | G1–G3 created (#155); G4 later |
| `widget/` | rainmaker.js (TypeScript, gts, Vite, Vitest; one script, max 15 KB gzipped) | npm | Set up empty (#155); panel in #152 |
| `portals/` | React apps for publishers and admins | npm | Empty – later |
| `infra/` | AWS CDK app (Java), one stack per environment; see `infra/README.md` | Maven module | **In progress** (#61–#63) |
| `docs/` | START_HERE.md, architecture decision records | – | Empty – later |
| `docs/study-guides.md` | How Claude writes and publishes a study guide for each PR, and the hub's link | – | Added in #179 |
| `docs/adr/` | Architecture decision records, one Markdown file per decision | – | First ADR: 0001, Mission Control V1 (#6) |

Build all Maven modules with `./mvnw verify` from the root (parent `pom.xml`, JDK 21
or newer; code targets Java 21).
Each Maven module is listed in its nearest aggregator POM: the root `pom.xml`, or an
aggregator such as `apps/mission-control/pom.xml` for modules inside it. A module's
`<parent>` is that same aggregator.

Planned services and apps (folder names to use when they are created):

| Group (app folder) | Services (folder under `services/`) |
|---|---|
| G1 Reader & clicks (`g1-reader-app`) | S1 Widget Delivery (`widget-delivery`), S2 Tracking & Click (`tracking-click`) |
| G2 Content & AI (`g2-content-app`) | S5 Content Ingestion (`content-ingestion`), S6 Content & Intent (`content-intent`), S7 Matching & Ranking (`matching-ranking`) |
| G3 Publisher & marketplace (`g3-publisher-app`) | S3 Publisher Control (`publisher-control`), S4 Advertiser Marketplace (`advertiser-marketplace`), S8 Inventory Integration (`inventory-integration`) |
| G4 Measurement & money (`g4-money-app`) | S9 Analytics & QTI (`analytics-qti`), S10 Clearing House (`clearing-house`) |

Base package per service: `se.rainmaker.<folder without dashes>` (e.g.
`se.rainmaker.clearinghouse`). Other modules may only use its `.api` sub-package.
Mission Control: `se.rainmaker.missioncontrol.<module>` (e.g.
`se.rainmaker.missioncontrol.collector`). Test site: `se.rainmaker.testsite`. Apps:
`se.rainmaker.<folder without dashes>` (e.g. `se.rainmaker.g1readerapp`); each app scans only
the service packages it assembles, and its `ModuleBoundaryTest` enforces the `.api` rule.

## Claude behaviour

### Working on a task
- Work from a GitHub issue. If the acceptance criteria are missing or ambiguous,
  stop and ask. Do not guess requirements.
- Stay within the scope of the issue. Note unrelated problems you find in the
  summary; do not fix them in the same change.
- Follow the existing patterns in the module before introducing new ones.
- Do not invent APIs, library methods, configuration keys or AWS services.
  If you are unsure something exists, check the code or the docs, or say so.
- Do not add new dependencies without stating why and asking first.
- Use the domain terms defined in `docs/CONTEXT.md` (once it exists) in code, tests
  and issues. Do not use the synonyms it lists as ones to avoid.
- If a change contradicts an architecture decision record in `docs/adr/`, say so
  explicitly and ask before proceeding. Do not silently override it.
- Never read, print or echo secret values into the session, whatever the source:
  environment variables (`env`, `printenv`), `.env` files (`cat .env`), Secrets Manager
  (`aws secretsmanager get-secret-value`), SSM SecureString parameters
  (`--with-decryption`) or `cdk synth` output. Reference secrets by name or ARN; in CDK
  use `SecretValue.secretsManager(...)`.

### Be critical of your own code
Before presenting any change, review it as a strict senior reviewer would:
- Does it actually meet every acceptance criterion? Map each one to code and a test.
- Edge cases: nulls, empty collections, duplicates, retries, timeouts, concurrency.
- Error handling: no swallowed exceptions, no generic `catch (Exception e)`
  without a reason, failures are logged with context.
- Security: input validation, authorisation checks, no secrets, no personal data
  in logs.
- Does it break any rule in this file, especially the product rules?
- Is there a simpler way? Remove dead code, duplication and speculative abstractions.

Before opening a draft PR, fix the gaps this review finds instead of only listing
them, and say in the PR summary what you found and fixed. State the weaknesses,
assumptions and open risks that remain. Never present code as finished or
"production-ready" without this review.

### Study guides
- After opening a draft PR that changes code (not for docs or config only), write a
  study guide for it, in English: one page that explains all the changed code, with
  questions at the end that check the developer understood it. Publish it as a
  private claude.ai page and add it to the team's hub. How, and what a guide
  contains: `docs/study-guides.md`.
- Then remind the developer to share the guide with the team from its Share menu.
  Claude cannot change who has access to a page.
- When the PR changes after a review, update the guide (same link) and its hub entry.

### Verify, don't assume
- Run the build, formatter and relevant tests before saying a change works.
- While iterating, build only the module you change, optionally with a single test:
  `./mvnw -pl apps/mission-control/collector -am verify -Dtest=ClassName#method -Dsurefire.failIfNoSpecifiedTests=false`.
  `-am` also builds the modules it depends on, which do not have that test; without the
  last flag they fail with "No tests matching pattern". With it, a misspelled test name
  passes silently, so check that `Tests run:` in the output includes your test.
  Run the full `./mvnw verify` from the root before saying the change is done.
- Report results honestly: if tests fail, are skipped or could not be run, say so
  with the output.
- Never weaken, skip, delete or `@Disabled` a test to make the build pass.
- Never bypass hooks or checks (`--no-verify`, disabling Checkstyle, etc.).

### Pull requests
- When opening a PR, add a summary comment that explains:
  1. **What the code does** - behaviour in plain language, not a file list.
  2. **Why** - the linked issue and acceptance criteria it covers.
  3. **How it was verified** - tests added/run and their result.
  4. **Risks and open points** - assumptions, known limitations, follow-ups.
  5. **AI involvement** - what Claude generated and what the developer changed.
- Claude **never approves or merges a pull request**, on any branch.
- Final approval of every PR to `main` is given by a human reviewer.
- Claude never pushes directly to `main`, `develop` or `release/*`.
- Claude may commit to the current feature branch, and push a `feature/*` branch it
  created for the task once the module build passes. Never force-push, and never push
  a branch someone else is working on.
- Claude opens a PR only when asked, and then as a draft (`gh pr create --draft`). The
  developer completes the "AI involvement" section and marks the PR ready for review.

## Code standards
- Java 21 + Spring Boot 4.1, Google Java Style Guide (google-java-format, Checkstyle).
  `./mvnw verify` enforces both (see README.md, "Code quality"). Run
  `./mvnw spotless:apply` before committing; Checkstyle fails on any warning, including
  missing Javadoc on public classes and methods.
- Spring Boot 4 differs from Boot 3, and most examples online are still Boot 3. Check the
  Boot 4 reference docs and migration guide instead of copying old examples:
  - Starters are split per technology, with matching test starters (e.g.
    `spring-boot-starter-webmvc` + `spring-boot-starter-webmvc-test`).
  - JSON uses Jackson 3 (package `tools.jackson`, not `com.fasterxml.jackson`).
  - Mock beans in tests with `@MockitoBean`; `@MockBean` is gone.
- TypeScript (React portals, rainmaker.js widget) follows Google TypeScript style (gts;
  set up for Mission Control in #8).
- Use constructor injection. NEVER `@Autowired` on fields.
- Descriptive names. No abbreviations or names like `helper2`.
- No one-letter words in method names (`A`, `I`): Checkstyle reads `refusesASource` as the
  abbreviation `AS` and fails the build. Write `refusesSource` or `refusesUnknownSource`.
- Small classes and methods with a single responsibility. No god classes.
- No `TODO` without a linked issue number (`// TODO(#123): ...`).
- Never commit secrets, API keys, or passwords. Secrets live in AWS Secrets Manager.
- No hard-coded UI text, category names or pick-list values. Everything comes from
  Swedish/English translation files.
- Database changes only through versioned Flyway migrations. Never edit a
  migration that has been merged.

## Testing
- Tests first: write tests from the acceptance criteria before implementation.
- Unit tests: JUnit 5, AssertJ, Mockito. Minimum 80 % coverage on domain logic
  (JaCoCo; a module with domain logic switches the gate on, see README.md).
- Integration tests: Spring Boot Test + Testcontainers (PostgreSQL, Redis, LocalStack).
- Contract tests for REST APIs and event schemas. A breaking change fails the build.
- External APIs are mocked with WireMock, never called from tests.
- Tests must be deterministic: no sleeps, no dependency on test order or wall clock.

## Architecture rules
- Each module owns its own database. Never read another module's database.
- A module may only use another module's public `api` package, never its internals.
- Each app (G1-G4) assembles services. Services do not depend on apps.
- Inside a group: interfaces + Spring events (outbox pattern). Between groups:
  REST for queries, EventBridge + SQS for events.
- External calls use timeouts, retries with back-off and circuit breakers (Resilience4j).
- ArchUnit tests enforce these boundaries. Each module or app that depends on other
  modules adds its rules (example in README.md); until a rule exists, reviewers check it.

## Product rules (do not break)
- Red articles and blocked categories NEVER get suggestions or a panel.
- Fail open for the reader: if Rainmaker fails, the publisher's page must load normally.
- Fail closed for money: clicks are idempotent on `click_id`, never lost, never
  counted twice.
- Click, settlement and audit records are append-only. Never update or delete them;
  correct a mistake with a new compensating record.
- Rank by relevance and quality. Price (CPC) only breaks ties.
- Never write to the publisher's CMS. Ingestion is read-only.
- Respect reader consent (TCF). No reader profiles, no personal data in logs.
- Every zone override, activation and money movement is audit-logged.

## Logging and monitoring
- All log output must be structured JSON with `trace_id` and `correlation_id`.
- Never log reader IPs, e-mails or free text from readers.
- Never use `trace_id`, `click_id`, URLs or user IDs as metric labels.
- A service is not done until it has RED metrics, structured logs, a dashboard
  and at least one alert with a runbook link.

## Git and PR rules
- Git Flow: `feature/<issue-number>-short-name` from `develop` (e.g.
  `feature/12-github-issues-source`); `release/x.y.0` and `hotfix/x.y.z` per the
  branching strategy.
- Conventional Commits with the GitHub issue number (`feat(#12): ...`, `fix:`,
  `test:`, `chore:`). AI-assisted commits carry a `Co-Authored-By` trailer for Claude.
- PRs under ~400 changed lines. Split larger work.
- Every PR needs at least one approval from a developer other than the author,
  green CI and all conversations resolved.
- Senior review required for: G4 money logic (S9, S10), authentication (Cognito),
  security code and infrastructure (CDK).

## Definition of done
- Acceptance criteria met and demonstrated.
- Tests written and passing in CI; no new critical static-analysis findings
  (once a static-analysis tool is added).
- Logging, metrics and translations in place.
- Docs updated (module README, `START_HERE.md` if status or decisions changed).
- PR summary written, reviewed and approved by a human.

## Infrastructure
- All AWS resources are created with AWS CDK (Java) through the pipeline.
  Never suggest creating resources by hand in the AWS console.
- Every infrastructure PR includes the `cdk diff` output.
- Region: eu-north-1 (Stockholm). Use the smallest sizes in dev and UAT.

## Changing these rules
- When the same review comment appears more than once, propose it as a rule here
  (or in the module's own `CLAUDE.md` if it only applies to that module).
- Changes to this file go through a normal PR and are reviewed at the Wednesday
  AI workshop.
