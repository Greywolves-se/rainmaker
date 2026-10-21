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
