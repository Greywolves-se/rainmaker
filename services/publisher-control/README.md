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

## What it does so far (#150, part 1)

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
- **Not yet:** the editor page, and the approved links API for G1 (contract 7.3).
