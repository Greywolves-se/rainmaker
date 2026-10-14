# S3 Publisher Control

Publishers, sites, users, per-site policy, the article activation workflow, overrides and the audit log.

| | |
|---|---|
| Events | Emits ArticleActivated / ArticleDeactivated. |
| Hot path | No |
| Deployed in | G3 (`apps/g3-publisher-app`) |
| Base package | `se.rainmaker.publishercontrol` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.publishercontrol.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
