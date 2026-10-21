# S6 Content & Intent (`content-intent`)

Classifies each article with Claude on Amazon Bedrock: topics, brand-safety zone and a short reason.

- **Runs in:** G2 (`apps/g2-content-app`)
- **Built in:** #148 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.contentintent`. Other modules may only use `se.rainmaker.contentintent.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own database, never another module's (architecture D16).
