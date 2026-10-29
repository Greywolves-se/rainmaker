# S1 Widget Delivery (`widget-delivery`)

Gives rainmaker.js the approved links for an article.

- **Runs in:** G1 (`apps/g1-reader-app`)
- **Built in:** #151 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.widgetdelivery`. Other modules may only use `se.rainmaker.widgetdelivery.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Serves rainmaker.js** at `/widget/rainmaker.js` (#152, contract D-7): `script/WidgetScriptController`
  reads the built file from `rainmaker.widget-delivery.script-location`, with `Cache-Control: no-cache`,
  and answers 404 until `npm run build` in `widget/` has made it. Only for the demo: later S1 delivers
  the script through a CDN (architecture S1), and this goes away.
- **Asks G3 for the approved links** (#151 part 1, contract 7.3): `links/G3ApprovedLinksClient` calls
  `GET /api/approved-links?url=…` with `X-Correlation-Id`, on every hover. rainmaker.js waits at most
  2 s, so the settings (`rainmaker.widget-delivery.approved-links.*`, G3's address from
  `RAINMAKER_G3_URL`) keep G1's wait at 1.55 s at most: 250 ms to connect, 500 ms to read, one retry
  after 50 ms. Only a lost connection, a timeout or a 5xx is retried. While G3 keeps failing (half of
  the last 10 calls, at least 5), a circuit breaker stops calling it for 10 s. G1 doesn't start if
  the settings could make it wait 2 s or more (#224).
  - **Fails closed:** if G3 can't answer, the client throws `ApprovedLinksUnavailableException`, and
    the article gets no links, never the last known ones. A link G3 sent without all its fields is
    left out.
  - The tests read G3's answer from `docs/rainmaker/contract/7.3-approved-links.json`, the contract's
    own example, so G3's tests can use the same file.
- **The widget API** (#151 part 2, contract 7.4): `GET /widget/v1/links?url=…`, which rainmaker.js
  calls on a headline hover. `links/WidgetLinksController` checks the address, and
  `links/WidgetLinksService` asks G3 and turns its approved links into the panel's: at most 3, in
  rank order, each with a new signed click link from S2 (`ClickLinks`, contract 7.5) instead of the
  offer's address.
  - **200** with the links, or with `"links": []` for any article G3 gives none (red, yellow,
    unclassified, unknown, nothing approved). Nothing is cached, so an article that turns red
    loses its links on the next hover. A link S2 can't sign is left out, not the whole panel.
  - **400** when `url` is missing, longer than 2048 characters, not an absolute http(s) address,
    or not on one of the publisher sites in `allowed-origins` (#224); G3 isn't asked. Otherwise
    random addresses could slow G3 and G2 and open the circuit for every reader. **503** when G3
    can't answer, never the last known links (fail closed).
  - `Cache-Control: no-store`, no login and no cookies. CORS only for the sites in
    `rainmaker.widget-delivery.widget-api.allowed-origins` (the demo: `http://localhost:3000`),
    `GET` only, without credentials. Nothing about the reader is logged; each request gets its own
    correlation id, sent to G3 and put in the log context.
  - The tests check G1's answer against `docs/rainmaker/contract/7.4-widget-links.json`, and
    rainmaker.js's tests read the same file, so the two sides can't drift.
  - **The click links must be on the script's origin.** rainmaker.js drops every link whose
    `clickUrl` isn't on the origin it was loaded from, but G1 builds the links from S2's own setting,
    `RAINMAKER_CLICK_BASE_URL`. G1 can't check this, because it doesn't know where the script is
    loaded from. If the two differ, even `localhost` against `127.0.0.1`, every panel disappears
    without an error.
- **Data:** no database of its own for the demo: it asks G3 for the approved links on each
  request (contract, section 7.3). A Redis projection comes later (architecture S1).
