# S5 Content Ingestion (`content-ingestion`)

Reads new and edited articles from the publisher's feed (in the demo, the test site's RSS feed) and stores them.

- **Runs in:** G2 (`apps/g2-content-app`)
- **Built in:** #147 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.contentingestion`. Other modules may only use `se.rainmaker.contentingestion.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own PostgreSQL database `content_ingestion` with its own login, never another module's
  (architecture D16). `database/ContentIngestionDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/content-ingestion/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(ContentIngestionDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(ContentIngestionDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.
