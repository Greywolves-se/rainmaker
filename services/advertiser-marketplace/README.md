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

## What it does (#149)

- **Offers:** the `offer` table (`V2__offer.sql`) holds the demo's eight fictional offers
  (`V3__fictional_offers.sql`): advertiser, title, text, a commercial category key (#145, D-3) and the CPC in
  whole öre. To change an offer, add a new migration.
- **`GET /api/offers`:** every offer, as in the contract (`docs/rainmaker/contract.md`, 7.2). Matching in G2
  reads it.
- **`api.OfferCatalog`:** the same offers for modules inside G3, e.g. the editor page (#150).
- **Landing pages:** `GET /landing/{offerId}` shows a simple page with the offer and a "Fictional test content"
  label. An offer's `destinationUrl` is `rainmaker.advertiser-marketplace.landing-base-url` (the G3 app's own
  address) plus `/landing/{offerId}`, so no address is stored in the database.
  **Demo only:** every offer is fictional. Real advertisers will need their own URL stored per offer (a new
  `destination_url` column), and the landing pages would then only be for test offers.
  **In Docker** (#146), set `RAINMAKER_ADVERTISERMARKETPLACE_LANDINGBASEURL` to the address the browser uses for
  G3 (e.g. `http://localhost:8083`); Spring reads it as `rainmaker.advertiser-marketplace.landing-base-url`.
- **Text:** the page's labels and the category names come from
  `src/main/resources/advertiser-marketplace/messages*.properties`: Swedish by default, English when the browser
  asks for it. The offer's own text is shown as stored, always HTML-escaped.
