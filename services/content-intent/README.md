# S6 Content & Intent Engine

LLM classification (Claude on Bedrock): topics, geography, commercial relevance, green/yellow/red zone, context index 0–100 with explanation; embeddings. Build week 2.

| | |
|---|---|
| Events | Emits ArticleClassified. |
| Hot path | No (async) |
| Deployed in | G2 (`apps/g2-content-app`) |
| Base package | `se.rainmaker.contentintent` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.contentintent.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
