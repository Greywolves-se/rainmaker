# START HERE - Rainmaker

Read this first. It tells a new person or AI what we are building, where we are,
and what to work on next. Details live in *Rainmaker Solution Architecture v0.5*
(4 Oct 2026).

> Last updated: 14 Oct 2026 (Sprint 1, week 1).
> This file is maintained by hand until Mission Control V1 can generate it.
> Update **Current status**, **Next work** and **Open questions** at every sprint end.

---

## 1. What we are building

News sites have articles (travel, training, moving abroad...) that no one earns
money from. Rainmaker adds one async script tag to the publisher's site. When a
reader hovers a headline, a small labelled panel shows a few relevant commercial
links. The AI suggests the links, an editor approves them, and every click is
tracked so the advertiser can be charged and the revenue split.

```
Article -> ingest -> AI classifies (green/yellow/red) -> AI suggests links
        -> editor approves -> reader sees panel -> click tracked -> monthly report
```

**Not in the pilot:** e-paper/PDF, video, native apps, advertiser self-service,
Stripe, prepaid budgets, automated payouts, ad auctions, Salesforce.

## 2. Goals and dates

| Date | Milestone |
|---|---|
| 12 Oct 2026 | Development starts |
| 18 Dec 2026 | **Goal 1:** full pilot flow works and is tested in UAT |
| 21 Dec - 6 Jan | Change freeze, bug fixes only |
| Week of 4 Jan | Go/no-go decision (Peter) |
| ~7 Jan 2027 | **Goal 2:** external pilot at the pilot publisher (UNT, not yet confirmed) |

Pilot target: >= 50 activated articles with a working panel, clicks, QTI baseline
and cost per article/click measured.

## 3. Current status

| Area | Status |
|---|---|
| Architecture v0.5 | Delivered, for approval |
| Monorepo | Created 14 Oct (`Greywolves-se/rainmaker`, private). Folder structure in review (#33). No CI yet |
| Mission Control V1 | Planned: issues #1–#32, milestone "Mission Control V1". Not started |
| AWS accounts + cost guard-rails | Waiting for senior set-up; nothing installed yet |
| Fake-news test site | Prototype runs locally (Valdmora Dagblad, 40 articles, RSS, news sitemap); not yet in the repo (owner: Kalle) |
| Pilot publisher | Not confirmed (critical) |

## 4. Architecture in one page

Ten logical services, deployed as four Spring Boot apps (modular monoliths) on
AWS ECS Fargate, eu-north-1.

| App | Services | Does what |
|---|---|---|
| G1 Reader & clicks | S1 Widget Delivery, S2 Tracking & Click | Serves links to the widget (< 50 ms), click redirect (< 100 ms), fraud filter |
| G2 Content & AI | S5 Ingestion, S6 Content & Intent, S7 Matching | Imports articles, LLM classification, link suggestions |
| G3 Publisher & marketplace | S3 Publisher Control, S4 Advertiser Marketplace, S8 Inventory Integration | Editor approve/reject, advertisers and offers, GAM/CMS imports |
| G4 Measurement & money | S9 Analytics & QTI, S10 Clearing House | Dashboards, QTI, click records, monthly settlement basis |

Plus **Mission Control** (internal status app) and **rainmaker-e2e** (end-to-end tests).

**Stack:** Java 21, Spring Boot 3, React + TypeScript, PostgreSQL + pgvector,
Redis, Claude via Amazon Bedrock, EventBridge + SQS, AWS CDK (Java),
GitHub Actions, OpenTelemetry + Grafana.

**Environments:** `develop` -> dev · `release/*` -> UAT (fake-news test site) ·
`main` -> prod (manual approval).

**Key rules:** one database per module · modules talk only via public APIs and
events · red articles never get a panel · fail open for the reader · clicks are
idempotent on `click_id` · rank by relevance, not price. Full list in `CLAUDE.md`.

## 5. Decisions already made

| # | Decision |
|---|---|
| B12 / D7 | Monorepo, one module per logical service |
| B13 / D8 | React + TypeScript front end |
| B14 / D16 | One RDS instance, one database per module |
| B23 / D1 | 10 logical modules deployed as 4 groups |
| B24 | AI proposes, editor approves/rejects/corrects |
| B4 | Test site uses AI-generated fake news (no copyrighted articles) |
| B9 | Google coding standard for all generated code |
| B11 | `main` = production, release branch = UAT |
| B27 | Pilot licence invoiced via Aurora; Stripe and full clearing later |
| B33 | No Python unless a concrete need appears |

**Recommended, waiting for approval:** ECS Fargate (D2), EventBridge + SQS (D3),
pgvector (D4), Bedrock (D5), RSS/sitemap first for CMS (D6), CDK in Java (D11),
AWS-managed Grafana/Prometheus (D13).

## 6. Open questions that block us

| # | Question | Owner | Needed by |
|---|---|---|---|
| C2 | Is UNT confirmed? Which CMS, feeds, permission to add a script? | Peter | Now - critical |
| C3 | How does the panel work on mobile? | Peter | Now - critical |
| C12 | Claude/AWS accounts and budget via Aurora | Staffan / Simon | Now |
| C13 | NDA, student agreements, IP transfer signed | Staffan / Simon | Now |
| C18 | Pilot agreement and what the pilot licence covers | Staffan | Mid-Nov |
| C6 | Green/yellow/red policy + ~200 labelled articles | Peter + publisher | Nov |
| C5 | Number of suggestions / links per article (proposal 3 / 2) | Peter | Nov |
| C8 | Ad label wording (sv/en) | Publisher | Nov |
| C7 | QTI definition and signals | Peter | Dec |

Until C3 is decided, build mobile behaviour behind a feature flag.

## 7. Risks

- **Pilot publisher, feed access or agreement not confirmed in time.**
  Mitigation: develop against the fake-news test site so we never wait for real data.
- **Mobile interaction undecided.** Mitigation: feature flag.
- **Unexpected AWS bill.** Mitigation: budgets, alerts and region lock before any
  resource; only CDK through the pipeline.
- **Short timeline, new team.** Mitigation: fixed pilot scope, demos every two weeks.

## 8. Next work

**This week (12-16 Oct): set-up and Mission Control V1**
1. Accounts for GitHub, Slack, Drive and Claude for everyone.
2. Review and merge the monorepo structure (#33); turn on branch protection for `main` and `develop`.
3. Mission Control V1, first demo slice: #7, #8, #10, #11, #12, #15, #16, #18, #20, #22
   (status board with real GitHub data + decision log). Order of work: see the plan PDF in Drive.
4. CI in GitHub Actions for Mission Control (#9).
5. Seniors: AWS accounts, SSO, budget alarms, region lock.
6. First Rainmaker user stories as GitHub issues.

**Next (19-30 Oct):** fake-news test site, S5 article import, labelled evaluation
set, S6 LLM classification, first end-to-end slice.

## 9. Who to ask

| Topic | Person |
|---|---|
| Product, scope, go/no-go | Peter (only for business/user/legal/budget impact) |
| Money, contracts, accounts via Aurora | Staffan |
| Architecture, technical decisions, blockers | Simon Öhlmér |
| Coding standard, fake-news test site | Kalle Johansson |
| Test suite, UAT | Raghavendra M R |
| AI guidelines, Wednesday workshops | Joakim Samuelsson |

Escalate to Peter with a recommendation, not just a problem.

## 10. Where things live

| What | Where |
|---|---|
| Code, issues, PRs, technical decisions | GitHub – `Greywolves-se/rainmaker` (monorepo) |
| Agreements, documents, architecture document | Google Drive |
| Daily questions | Slack (not the long-term memory) |
| Status, decisions, risks | Mission Control – `apps/mission-control`, runs locally in V1 (mission.rainmaker.nu on AWS later) |
| Architecture decision records | `docs/adr/` |
| Claude rules | `CLAUDE.md` in the repo root |

## 11. Words

- **Zone:** green = OK, yellow = check first, red = never (crime, tragedies).
- **QTI:** Qualified Traffic Index. How much better our clicks are than the site's normal clicks (100 = same).
- **CPC:** cost per click. **GAM:** Google Ad Manager. **UAT:** test environment where Peter accepts releases.
- **Ad ops:** publisher staff who approve commercial links.
- **Strike Force:** the development team.
