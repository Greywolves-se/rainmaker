# S9 Analytics & QTI

Aggregates events, reference CTR and conversion postbacks; computes the Qualified Traffic Index; dashboard APIs.

| | |
|---|---|
| Events | Consumes Click* and Conversion events. |
| Hot path | No |
| Deployed in | G4 (`apps/g4-money-app`) |
| Base package | `se.rainmaker.analyticsqti` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.analyticsqti.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
