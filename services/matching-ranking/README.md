# S7 Matching & Ranking (`matching-ranking`)

Suggests offers for green articles, ranked by relevance; CPC only breaks ties.

- **Runs in:** G2 (`apps/g2-content-app`)
- **Built in:** #149 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.matchingranking`. Other modules may only use `se.rainmaker.matchingranking.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own PostgreSQL database `matching_ranking` with its own login, never another module's
  (architecture D16). `database/MatchingRankingDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/matching-ranking/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(MatchingRankingDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(MatchingRankingDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.

## What it does (#149, part 2a)

The building blocks of matching; part 2b stores the suggestions and listens for content-intent's
`ArticleClassified` event (contract 6.2).

- **Ranking** (`ranking/OfferRanking`, contract section 4): an offer fits when its category is one of the
  article's topics, and the earlier the topic, the better the fit; CPC only breaks ties, then the offer id.
  At most 3; `other` never matches; an offer with an unknown category is skipped and logged.
- **G3's offers** (`offers/G3OffersClient`): reads `GET /api/offers` (contract 7.2), with a connect and a
  read timeout and retries with back-off on no connection, a timeout or a 5xx. A 4xx or an unusable answer
  is not retried.

Settings, `rainmaker.matching-ranking.offers.*` (checked at start-up; values in the G2 app's
`application.properties`):

| Setting | Default in G2 | What |
|---|---|---|
| `base-url` | `${RAINMAKER_G3_URL:http://localhost:8083}` | G3's address; set `RAINMAKER_G3_URL` in Docker (#146) |
| `connect-timeout`, `read-timeout` | `2s`, `5s` | How long to wait for G3 |
| `max-attempts`, `backoff` | `3`, `500ms` | Retries on no connection, a timeout or a 5xx; the wait doubles each time |
