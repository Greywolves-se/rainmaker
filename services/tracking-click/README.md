# S2 Tracking & Click (`tracking-click`)

The click redirect: stores each click once and sends the reader to the offer.

- **Runs in:** G1 (`apps/g1-reader-app`)
- **Built in:** #153 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.trackingclick`. Other modules may only use `se.rainmaker.trackingclick.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own database, never another module's (architecture D16).
