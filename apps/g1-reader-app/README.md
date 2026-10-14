# G1 Reader experience & clicks

Widget, links, click events. Hot path – scaled separately.

Deployable Spring Boot application (modular monolith) that assembles these services:

- S1 Widget Delivery API – `services/widget-delivery`
- S2 Tracking & Click – `services/tracking-click`

Inside the app, modules talk through interfaces and Spring events. Between apps: REST for queries, EventBridge + SQS for events (architecture v0.5, section 5.2).

Empty so far – the Spring Boot application class and the `spring-boot-maven-plugin` are added with the first real feature.
