# S2 Tracking & Click

Receives hover/impression beacons and the click redirect `/c/{token}`; bot and fraud filters; publishes ClickValidated.

| | |
|---|---|
| Events | Emits ClickValidated, ClickRejected. |
| Hot path | Yes – redirect < 100 ms |
| Deployed in | G1 (`apps/g1-reader-app`) |
| Base package | `se.rainmaker.trackingclick` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.trackingclick.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
