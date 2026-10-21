# G1 Reader & clicks (`g1-reader-app`)

Serves rainmaker.js its links and handles clicks. The hot path: it must stay fast and fail open. One of Rainmaker's deployable apps (architecture 5.2, decided for the demo on #145).

| Service | Module | Built in |
|---|---|---|
| S1 Widget Delivery | [`services/widget-delivery`](../../services/widget-delivery) | #151 |
| S2 Tracking & Click | [`services/tracking-click`](../../services/tracking-click) | #153 |

- **Port:** 8081 (`server.port` in `src/main/resources/application.properties`).
- **Run:** `./mvnw -pl apps/g1-reader-app -am spring-boot:run` from the repository root.
- **Talks to the other apps** only through REST queries (#145). Services never depend on apps,
  and other code may only use a service's `api` package; `ModuleBoundaryTest` enforces both.
