# G3 Publisher & marketplace

Editorial control, simple advertiser and offer management, and the CMS or GAM connection needed for the pilot.

Deployable Spring Boot application (modular monolith) that assembles these services:

- S3 Publisher Control – `services/publisher-control`
- S4 Advertiser Marketplace – `services/advertiser-marketplace`
- S8 Inventory Integration – `services/inventory-integration`

Inside the app, modules talk through interfaces and Spring events. Between apps: REST for queries, EventBridge + SQS for events (architecture v0.5, section 5.2).

Empty so far – the Spring Boot application class and the `spring-boot-maven-plugin` are added with the first real feature.
