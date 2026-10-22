# G2 Content & intelligence (`g2-content-app`)

Reads articles, classifies them with Claude on Amazon Bedrock and suggests offers. One of Rainmaker's deployable apps (architecture 5.2, decided for the demo on #145).

| Service | Module | Built in |
|---|---|---|
| S5 Content Ingestion | [`services/content-ingestion`](../../services/content-ingestion) | #147 |
| S6 Content & Intent | [`services/content-intent`](../../services/content-intent) | #148 |
| S7 Matching & Ranking | [`services/matching-ranking`](../../services/matching-ranking) | #149 |

- **Port:** 8082 (`server.port` in `src/main/resources/application.properties`).
- **Run** (from the repository root): build the app with the services it needs, then start
  its jar:

  ```
  ./mvnw -pl apps/g2-content-app -am package -DskipTests
  java -jar apps/g2-content-app/target/g2-content-app-0.1.0-SNAPSHOT.jar
  ```

  `spring-boot:run` together with `-am` doesn't work: Maven then runs it on every module it
  builds, and the root POM and the services have no main class.
- **Talks to the other apps** only through REST queries (#145). Services never depend on apps,
  and other code may only use a service's `api` package; `ModuleBoundaryTest` enforces both.
