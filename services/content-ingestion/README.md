# S5 Content Ingestion

Polls RSS/sitemaps and CMS APIs or crawls URLs; extracts clean text (Jsoup); dedupes; stores article versions. Build week 2.

| | |
|---|---|
| Events | Emits ArticleIngested. |
| Hot path | No |
| Deployed in | G2 (`apps/g2-content-app`) |
| Base package | `se.rainmaker.contentingestion` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.contentingestion.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
