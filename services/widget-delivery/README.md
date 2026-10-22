# S1 Widget Delivery (`widget-delivery`)

Gives rainmaker.js the approved links for an article.

- **Runs in:** G1 (`apps/g1-reader-app`)
- **Built in:** #151 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.widgetdelivery`. Other modules may only use `se.rainmaker.widgetdelivery.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own database, never another module's (architecture D16).
