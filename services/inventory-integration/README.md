# S8 Inventory Integration

Adapters for Google Ad Manager, affiliate networks and product feeds; crawls and caches the top ~120 destination sites.

| | |
|---|---|
| Events | Emits DestinationUpserted. |
| Hot path | No |
| Deployed in | G3 (`apps/g3-publisher-app`) |
| Base package | `se.rainmaker.inventoryintegration` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.inventoryintegration.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
