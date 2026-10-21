# ADR 0004: The Rainmaker demo runs as three apps that talk over REST

- **Status:** Accepted by the Rainmaker team (21 Oct 2026), for the demo on 30 Oct 2026.
- **Date:** 21 Oct 2026
- **Issue:** #145. The contract itself: [`docs/rainmaker/contract.md`](../rainmaker/contract.md).

## Context

The demo on 30 Oct 2026 must work end to end: fake-news article → RSS → Claude classification →
matched offers → editor approval → panel on hover → click redirected and stored. Three people
build the three parts in parallel, in ten days.

The root `CLAUDE.md` groups the services into apps G1–G4. Inside an app, modules use interfaces
and Spring events (outbox pattern). Between apps: REST for queries, **EventBridge + SQS for
events**. AWS is "not now" for the demo.

## Decision

**Three apps, as in `CLAUDE.md`:** G1 `g1-reader-app` (8081: widget delivery, click tracking,
rainmaker.js), G2 `g2-content-app` (8082: ingestion, classification, matching) and G3
`g3-publisher-app` (8083: editor, offers). G4 (measurement and money) is not part of the demo.

**Departure from `CLAUDE.md`: no events between the apps.** Everything between apps is REST:

- G3 learns about new articles, classifications and suggestions by **polling** G2
  (`GET /api/articles?since=…`), instead of receiving events through EventBridge + SQS.
- G2 reads the offers from G3 (`GET /api/offers`) when it matches.
- G1 asks G3 for the approved links on every hover, and G3 asks G2 whether the article is still
  green (three hops).
- A click needs no other app: the signed click link carries the destination (contract, 7.5).

**Inside G2**, ingestion, classification and matching use Spring events, as `CLAUDE.md` says,
but without an outbox for now: an event lost in a crash is accepted, because ingestion re-reads
the feed.

## Consequences

- No AWS resources are needed for the demo, and each app can run and be tested on its own.
- **Polling delay:** a new article reaches the editor view after up to one polling interval (a
  setting of a few seconds).
- **Three hops per hover** (G1 → G3 → G2). Fine for the demo; caching is out of scope.
- **Start-up order:** G2 and G3 call each other, so each app starts, and keeps retrying, while
  the other isn't up yet.
- **Brand safety fails closed:** if G2 or G3 can't be reached, the widget gets no links.
- **After the demo:** replace polling with EventBridge + SQS events between the groups, and add
  the outbox inside each app (a new dependency, so it needs approval). The event shapes in the
  contract stay the same.

## Alternatives considered

- **One app with all seven modules:** every hand-over in-process, the least plumbing and no
  three-hop hover, but it departs from the G1–G4 layout. The team chose three apps instead
  (21 Oct 2026).
- **Events between the apps with EventBridge + SQS now:** the target architecture, but it needs
  AWS resources (CDK, credentials, LocalStack in tests) that the demo can't wait for.
