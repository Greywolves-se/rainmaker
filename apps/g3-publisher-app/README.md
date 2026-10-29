# G3 Publisher & marketplace (`g3-publisher-app`)

The editor page with approvals, and the advertisers' offers and landing pages. One of Rainmaker's deployable apps (architecture 5.2, decided for the demo on #145).

| Service | Module | Built in |
|---|---|---|
| S3 Publisher Control | [`services/publisher-control`](../../services/publisher-control) | #150 |
| S4 Advertiser Marketplace | [`services/advertiser-marketplace`](../../services/advertiser-marketplace) | #149 |

- **Port:** 8083 (`server.port` in `src/main/resources/application.properties`).
- **Run** (from the repository root): start PostgreSQL, build the app with the services it
  needs, then start its jar:

  ```
  docker compose up -d postgres
  ./mvnw -pl apps/g3-publisher-app -am package -DskipTests
  java -jar apps/g3-publisher-app/target/g3-publisher-app-0.1.0-SNAPSHOT.jar
  ```

  `spring-boot:run` together with `-am` doesn't work: Maven then runs it on every module it
  builds, and the root POM and the services have no main class.
- **Talks to the other apps** only through REST queries (#145). Services never depend on apps,
  and other code may only use a service's `api` package; `ModuleBoundaryTest` enforces both.
