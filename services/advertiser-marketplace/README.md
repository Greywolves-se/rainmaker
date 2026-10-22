# S4 Advertiser Marketplace (`advertiser-marketplace`)

Advertisers, offers and their landing pages.

- **Runs in:** G3 (`apps/g3-publisher-app`)
- **Built in:** #149 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.advertisermarketplace`. Other modules may only use `se.rainmaker.advertisermarketplace.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own PostgreSQL database `advertiser_marketplace` with its own login, never another module's
  (architecture D16). `database/AdvertiserMarketplaceDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/advertiser-marketplace/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(AdvertiserMarketplaceDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(AdvertiserMarketplaceDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.
