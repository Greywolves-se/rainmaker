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
  the last 10 calls, at least 5), a circuit breaker stops calling it for 10 s.
  - **Fails closed:** if G3 can't answer, the client throws `ApprovedLinksUnavailableException`, and
    the article gets no links, never the last known ones. A link G3 sent without all its fields is
    left out.
  - The tests read G3's answer from `docs/rainmaker/contract/7.3-approved-links.json`, the contract's
    own example, so G3's tests can use the same file.
  - The widget API that uses it (`/widget/v1/links`, contract 7.4) is part 2.
- **Data:** no database of its own for the demo: it asks G3 for the approved links on each
  request (contract, section 7.3). A Redis projection comes later (architecture S1).
