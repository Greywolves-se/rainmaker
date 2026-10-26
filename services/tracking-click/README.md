# S2 Tracking & Click (`tracking-click`)

The click redirect: stores each click once and sends the reader to the offer.

- **Runs in:** G1 (`apps/g1-reader-app`)
- **Built in:** #153 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.trackingclick`. Other modules may only use `se.rainmaker.trackingclick.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own PostgreSQL database `tracking_click` with its own login, never another module's
  (architecture D16). `database/TrackingClickDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/tracking-click/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(TrackingClickDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(TrackingClickDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.
