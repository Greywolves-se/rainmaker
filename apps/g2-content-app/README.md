# G2 Content & intelligence

Fetch articles, classify, assess risk and propose relevant offers.

Deployable Spring Boot application (modular monolith) that assembles these services:

- S5 Content Ingestion – `services/content-ingestion`
- S6 Content & Intent Engine – `services/content-intent`
- S7 Matching & Ranking – `services/matching-ranking`

Inside the app, modules talk through interfaces and Spring events. Between apps: REST for queries, EventBridge + SQS for events (architecture v0.5, section 5.2).

Empty so far – the Spring Boot application class and the `spring-boot-maven-plugin` are added with the first real feature.
