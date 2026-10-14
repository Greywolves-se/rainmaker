# S7 Matching & Ranking

Vector search (pgvector) over offers plus rules (policy, exclusions, quality) → suggestions with reason and confidence; learns from editorial feedback. Ranks by relevance, not price.

| | |
|---|---|
| Events | Emits SuggestionsReady. |
| Hot path | No (async) |
| Deployed in | G2 (`apps/g2-content-app`) |
| Base package | `se.rainmaker.matchingranking` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.matchingranking.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
