# G1 Reader & clicks (`g1-reader-app`)

Serves rainmaker.js its links and handles clicks. The hot path: it must stay fast and fail open. One of Rainmaker's deployable apps (architecture 5.2, decided for the demo on #145).

| Service | Module | Built in |
|---|---|---|
| S1 Widget Delivery | [`services/widget-delivery`](../../services/widget-delivery) | #151 |
| S2 Tracking & Click | [`services/tracking-click`](../../services/tracking-click) | #153 |

- **Port:** 8081 (`server.port` in `src/main/resources/application.properties`).
- **Run** (from the repository root): start PostgreSQL, build the app with the services it
  needs, then start its jar:

  ```
  docker compose up -d postgres
  (cd widget && npm ci && npm run build)
  ./mvnw -pl apps/g1-reader-app -am package -DskipTests
  java -jar apps/g1-reader-app/target/g1-reader-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
  ```

  **The `local` profile** (`application-local.properties`) is for a developer's machine and the
  local Docker stack only. It points the click links at `http://localhost:8081` and lets G1 sign
  them with a random key for each run when `RAINMAKER_CLICK_SIGNING_KEY` isn't set. Without the
  profile, G1 doesn't start unless both `RAINMAKER_CLICK_BASE_URL` and the key are set (#153).

  The widget step builds `widget/dist/rainmaker.js`, which G1 serves at
  `http://localhost:8081/widget/rainmaker.js` (#152, contract D-7). G1 reads it from
  `rainmaker.widget-delivery.script-location` (default `file:widget/dist/rainmaker.js`, relative to
  the folder G1 is started from), on every request: after `npm run build`, reload the page, no
  restart. Without the build, the address answers 404 and the article page works without a panel.
  `npm ci` installs exactly the versions in `widget/package-lock.json`. A reload while
  `npm run build` is running can briefly get a 404 or a half-written file, because Vite empties
  `dist/` first; the page still works (fail open), and the next reload gets the new script.

  `spring-boot:run` together with `-am` doesn't work: Maven then runs it on every module it
  builds, and the root POM and the services have no main class.
- **Talks to the other apps** only through REST queries (#145). Services never depend on apps,
  and other code may only use a service's `api` package; `ModuleBoundaryTest` enforces both.
