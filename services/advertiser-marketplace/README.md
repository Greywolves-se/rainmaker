# S4 Advertiser Marketplace (`advertiser-marketplace`)

Advertisers, offers and their landing pages.

- **Runs in:** G3 (`apps/g3-publisher-app`)
- **Built in:** #149 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.advertisermarketplace`. Other modules may only use `se.rainmaker.advertisermarketplace.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own database, never another module's (architecture D16).
