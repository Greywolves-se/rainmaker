# S3 Publisher Control (`publisher-control`)

The editor page: approve or reject suggested offers, with an audit log.

- **Runs in:** G3 (`apps/g3-publisher-app`)
- **Built in:** #150 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.publishercontrol`. Other modules may only use `se.rainmaker.publishercontrol.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own database, never another module's (architecture D16).
