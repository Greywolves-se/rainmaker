# S6 Content & Intent (`content-intent`)

Classifies each article with Claude on Amazon Bedrock: topics, brand-safety zone and a short reason.

- **Runs in:** G2 (`apps/g2-content-app`)
- **Built in:** #148 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.contentintent`. Other modules may only use `se.rainmaker.contentintent.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own PostgreSQL database `content_intent` with its own login, never another module's
  (architecture D16). `database/ContentIntentDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/content-intent/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(ContentIntentDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(ContentIntentDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.
