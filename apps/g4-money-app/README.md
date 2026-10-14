# G4 Measurement & money

Basic dashboard, QTI signals and settlement basis. Senior review required for all code here (CLAUDE.md).

Deployable Spring Boot application (modular monolith) that assembles these services:

- S9 Analytics & QTI – `services/analytics-qti`
- S10 Clearing House – `services/clearing-house`

Inside the app, modules talk through interfaces and Spring events. Between apps: REST for queries, EventBridge + SQS for events (architecture v0.5, section 5.2).

Empty so far – the Spring Boot application class and the `spring-boot-maven-plugin` are added with the first real feature.
