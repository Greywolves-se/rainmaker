# S3 Publisher Control (`publisher-control`)

The editor page: approve or reject suggested offers, with an audit log.

- **Runs in:** G3 (`apps/g3-publisher-app`)
- **Built in:** #150 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.publishercontrol`. Other modules may only use `se.rainmaker.publishercontrol.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own PostgreSQL database `publisher_control` with its own login, never another module's
  (architecture D16). `database/PublisherControlDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/publisher-control/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(PublisherControlDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.

## What it does so far (#150)

> **No login.** The editor page is for the local demo only: anyone who can reach G3 can approve or
> reject, and every decision is saved as `demo-editor`. The page says so too. A login comes later
> (#150: "Not now").

- **The editor page:** <http://localhost:8083/publisher/articles> (`editor/`, contract 7.6).
  - It lists the 100 most recently changed articles: the headline, the zone (or "Not classified yet" /
    "Couldn't be classified") and Claude's reason.
  - A green article also gets its open suggestions: the offer's advertiser and title (from S4's
    `OfferCatalog`), the matched topic and the status, with Approve and Reject buttons. A red, yellow
    or unclassified article shows "No suggestions".
  - The buttons post to `/publisher/suggestions/{id}/approve` or `…/reject`, then go back to the page
    (303). An unknown suggestion gives 404; one that can't be decided any more gives 409. Both get a
    short page that says why.
  - Texts are in `src/main/resources/publisher-control/messages*.properties`, Swedish by default and
    English when the browser asks for it. Text from G2 and the offers is always HTML-escaped.

- **`articles/`** copies G2's articles and suggestions. Every `poll-interval` it asks G2's articles API
  for the changes since the last answer (`GET /api/articles?since=…`, contract 7.1), with timeouts and
  retries. If G2 is down, nothing changes and the next poll asks again, so G2 and G3 can start in any
  order.
  - A new article version withdraws the whole earlier set, even approved suggestions (D-5).
  - A suggestion missing from G2's current set is withdrawn.
  - An article that isn't classified + green keeps no open suggestions, whatever G2 sends (D-4).
- **Settings** (`rainmaker.publisher-control.articles.*`, in G3's `application.properties`): `base-url`
  (`RAINMAKER_G2_URL` in Docker), `connect-timeout`, `read-timeout`, `max-attempts`, `backoff`,
  `poll-interval`, and `polling=false` to turn the schedule off in tests.
- **`decision/SuggestionDecisions`** approves or rejects one suggestion and writes an audit row (who,
  when, what) in `suggestion_audit`. A withdrawn suggestion, or one of an article that isn't green,
  can't be decided. The same decision twice writes one row; a changed decision writes a new one. The
  database refuses any change to an audit row (append-only).
- **The approved links API for G1:** `GET /api/approved-links?url=…` (`links/`, contract 7.3).
  - A link is given only when the suggestion is approved, belongs to the article's latest version, and
    G2 says at that moment that this version is classified + green (contract 5).
  - G2 is asked on every call (`GET /api/articles?url=…`), with the caller's `X-Correlation-Id`. If G2
    can't answer, the API answers `503`, never the last known links (fail closed).
  - An unknown article gives `"articleId": null` and `"links": []`. A missing or non-http(s) `url`
    gives `400`.
  - `destinationUrl` is for G1's signed click link; it never goes to the reader's browser.
