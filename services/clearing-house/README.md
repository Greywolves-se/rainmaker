# S10 Clearing House

Pilot: append-only click records (idempotent on click_id) and the monthly settlement basis. Later: double-entry ledger, prepaid debit and split rules per revenue track.

| | |
|---|---|
| Events | Consumes ClickValidated; emits BudgetDebited. |
| Hot path | Near real-time |
| Deployed in | G4 (`apps/g4-money-app`) |
| Base package | `se.rainmaker.clearinghouse` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.clearinghouse.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
