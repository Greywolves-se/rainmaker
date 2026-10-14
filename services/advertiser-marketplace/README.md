# S4 Advertiser Marketplace

Advertisers, offers, destinations and categories (managed by publisher sales or Rainmaker in the pilot); prepaid accounts, budgets and max CPC later.

| | |
|---|---|
| Events | Emits CampaignChanged, BudgetExhausted. |
| Hot path | No |
| Deployed in | G3 (`apps/g3-publisher-app`) |
| Base package | `se.rainmaker.advertisermarketplace` |

## Rules
- This module owns its own database. Other modules never read it.
- Other modules may only use the public `se.rainmaker.advertisermarketplace.api` package.
- Source: Rainmaker Solution Architecture v0.5, section 5.1.

Empty so far – code arrives with the issues for this service.
