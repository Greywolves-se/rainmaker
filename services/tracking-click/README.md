# S2 Tracking & Click (`tracking-click`)

The click redirect: stores each click once and sends the reader to the offer.

- **Runs in:** G1 (`apps/g1-reader-app`)
- **Built in:** #153 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.trackingclick`. Other modules may only use `se.rainmaker.trackingclick.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Click links** (#153 part 1, contract 7.5, D-6): `api.ClickLinks` makes the link the widget gets
  for each approved link, `http://localhost:8081/c/<payload>.<signature>`:
  - the payload is base64url JSON with a new click id (UUIDv7), the article, the offer, the
    destination and an expiry (24 hours, `rainmaker.tracking-click.link.lifetime`);
  - the signature is HMAC-SHA256 with the key from `RAINMAKER_CLICK_SIGNING_KEY` (at least 32
    characters; never in code or a settings file). Without it, on a developer's machine, G1 signs
    with a random key for each run and logs a WARN: links served before a restart stop working;
  - `click/SignedClickLinks.check` reads a token back: valid, invalid (forged, changed, garbled) or
    expired. The signature is checked before anything in the payload is read, and the destination
    only ever comes from a signed link (no open redirect).
  - Opening a link (`GET /c/{token}`: store the click once, then redirect) is part 2.
- **Data:** its own PostgreSQL database `tracking_click` with its own login, never another module's
  (architecture D16). `database/TrackingClickDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/tracking-click/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(TrackingClickDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(TrackingClickDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.
