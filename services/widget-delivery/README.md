# S1 Widget Delivery API

Returns the approved links for an article URL to rainmaker.js. Read-only, served from Redis, cache-first; must never slow the publisher page.

| | |
|---|---|
| Events | Reads the ActivatedArticle projection. |
| Hot path | Yes – p95 < 50 ms |
| Deployed in | G1 (`apps/g1-reader-app`) |
| Base package | `se.rainmaker.widgetdelivery` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.widgetdelivery.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
