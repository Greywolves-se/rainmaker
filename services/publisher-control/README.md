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
    The app's `ModuleBoundaryTest` fails the build on an unnamed one, another module's, or
    `jakarta.transaction.Transactional` (#170).
