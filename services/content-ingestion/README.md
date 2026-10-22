# S5 Content Ingestion (`content-ingestion`)

Reads new and edited articles from the publisher's feed (in the demo, the test site's RSS feed) and stores them.

- **Runs in:** G2 (`apps/g2-content-app`)
- **Built in:** #147 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.contentingestion`. Other modules may only use `se.rainmaker.contentingestion.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own database, never another module's (architecture D16).
