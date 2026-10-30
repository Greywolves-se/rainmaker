# G2 Content & intelligence (`g2-content-app`)

Reads articles, classifies them with Claude on Amazon Bedrock and suggests offers. One of Rainmaker's deployable apps (architecture 5.2, decided for the demo on #145).

| Service | Module | Built in |
|---|---|---|
| S5 Content Ingestion | [`services/content-ingestion`](../../services/content-ingestion) | #147 |
| S6 Content & Intent | [`services/content-intent`](../../services/content-intent) | #148 |
| S7 Matching & Ranking | [`services/matching-ranking`](../../services/matching-ranking) | #149 |

- **Port:** 8082 (`server.port` in `src/main/resources/application.properties`).
- **Run** (from the repository root): start PostgreSQL, build the app with the services it
  needs, then start its jar:

  ```
  docker compose up -d postgres
  ./mvnw -pl apps/g2-content-app -am package -DskipTests
  java -jar apps/g2-content-app/target/g2-content-app-0.1.0-SNAPSHOT.jar
  ```

  `spring-boot:run` together with `-am` doesn't work: Maven then runs it on every module it
  builds, and the root POM and the services have no main class.
- **Articles API (#202, contract 7.1):** `GET /api/articles?recent=N` (1–500) and `?url=…`, for G3.
  `articles/CurrentArticles` combines the three services' `api` packages: the latest version
  (content-ingestion), its classification (content-intent) and its suggestions (matching), each
  read once for the whole list. Suggestions are only shown for a classified green version, and
  only that version's own set. Try it: `curl "http://localhost:8082/api/articles?recent=5"`.
- **Talks to the other apps** only through REST queries (#145). Services never depend on apps,
  and other code may only use a service's `api` package; `ModuleBoundaryTest` enforces both.
