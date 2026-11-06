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

## Logs and metrics (#193)

The pattern agreed on #193 for every Rainmaker app (#188 copies it for G3).

- **Logs:** one JSON object per line (Spring Boot's structured logging, ECS,
  `logging.structured.format.console=ecs`), with:
  - `trace_id` and `span_id`: a new trace per request (Micrometer Tracing with the OpenTelemetry
    bridge; nothing is exported yet). Renamed from `traceId` and `spanId` with
    `logging.structured.json.rename.*`;
  - `correlation_id`: a click's `clickId` (contract section 3), or a new id per widget API
    request, which G1 also sends to G3 as `X-Correlation-Id`.

  Never a reader's IP, a token, or free text from readers.
- **Metrics and health** on the management port **9081**, never on the readers' 8081:
  `http://localhost:9081/actuator/prometheus` and `/actuator/health`. Nothing else is exposed.
  `docker-compose.yml` publishes it on `127.0.0.1` only.

| Metric (Prometheus name) | Labels | What |
|---|---|---|
| `http_server_requests_seconds` | `method`, `uri` (route template), `outcome` | RED per endpoint: count, errors (`CLIENT_ERROR` / `SERVER_ERROR`) and time. `MetricsConfiguration` drops every other label |
| `rainmaker_clicks_total` | `result`: `stored`, `already_stored`, `invalid`, `expired`, `not_stored`, `looked_at` | Click links opened (S2) |
| `rainmaker_widget_links_total` | `result`: `links`, `empty`, `refused`, `unavailable` | Widget API answers (S1) |

A label is never an id, a token or a URL (CLAUDE.md); `G1ReaderApplicationTest` checks every
label value of these meters.

### First alerts and their runbook

No dashboard or alert exists yet: there is no AWS for the demo. When the monitoring stack is set
up with the infrastructure (#61–#63, CloudWatch or a managed Prometheus), these are the first
alerts, with this section as their runbook link.

- **Clicks not stored:** `increase(rainmaker_clicks_total{result="not_stored"}[5m]) > 0`.
  Readers see the "try again" page and nothing is redirected (fail closed for money, #192).
  1. Check S2's database: is PostgreSQL up, and can G1 connect (`/actuator/health`)?
  2. The log lines `Click not stored, so not redirected` carry the `correlation_id` (the
     `clickId`) and the database error.
  3. Nothing is lost: a reader who opens the same link again is stored once, with the same
     `clickId`.
- **Widget API failing:** `rainmaker_widget_links_total{result="unavailable"}` over 50 % of the
  answers for 5 minutes. Readers get no next step (fail open), so it costs revenue, not the page.
  1. Check G3 and its approved links API (contract 7.3): is it up, and is G2 up behind it?
  2. G1's circuit breaker logs one WARN when it opens (`G3 approved links circuit breaker CLOSED
     to OPEN`) and one when it closes.
  3. A rising `refused` instead means requests for addresses outside the publisher sites (#224).
