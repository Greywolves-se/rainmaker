# S7 Matching & Ranking (`matching-ranking`)

Suggests offers for green articles, ranked by relevance; CPC only breaks ties.

- **Runs in:** G2 (`apps/g2-content-app`)
- **Built in:** #149 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.matchingranking`. Other modules may only use `se.rainmaker.matchingranking.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own database, never another module's (architecture D16).
