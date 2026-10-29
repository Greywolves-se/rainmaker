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
    characters, trimmed; never in code or a settings file). G1 doesn't start without it, nor
    without `rainmaker.tracking-click.link.base-url` (`RAINMAKER_CLICK_BASE_URL`): a random key per
    G1, or links to localhost, would lose clicks. Only G1's `local` profile sets the address and
    allows a random key for each run, with a WARN;
  - every address (G1's own and each destination) must be a valid absolute http(s) URI
    (`click/HttpAddresses`). An advertiser's macro such as `{clickid}` must be percent-encoded when
    the offer is stored. `ClickLinks.create` throws for a bad destination, so the widget API
    (#151) drops that one link, not the panel;
  - `click/SignedClickLinks.check` reads a token back: valid, invalid (forged, changed, garbled) or
    expired. The signature is checked before anything in the payload is read, and the destination
    only ever comes from a signed link (no open redirect).
- **Opening a link** (#153 part 2, contract 7.5): `click/ClickRedirectController` answers
  `GET /c/{token}`:
  - valid: the click is stored (once per click id) and the reader is sent on with `302` to the
    destination inside the signed link;
  - forged, changed or garbled: a `400` page; expired: a `410` page. Neither stores anything;
  - the click can't be stored (the database is down): a `503` page and no redirect (fail closed for
    money). Opening the same link again retries with the same click id;
  - the pages' text is in `tracking-click/messages*.properties`: Swedish, or English when the
    browser asks for it;
  - the log has the click, article and offer ids, never the reader's IP or the token.
- **Stored clicks** (#153 part 2, contract 3.6): `click/ClickStore` writes one row per click id to
  the `click` table (`V2__click.sql`): click id, article, offer and time; no IP, cookie or user id.
  - The click id is the primary key and the insert is `ON CONFLICT DO NOTHING`, so a double-click,
    a retry or two requests at the same time are one row.
  - The table is append-only: the database refuses `UPDATE`, `DELETE` and `TRUNCATE` on it. A
    mistake is corrected with a new compensating record (CLAUDE.md, product rules).
  - `ClickStoreTest` runs against a real PostgreSQL (Testcontainers), so Docker must be running.
- **Data:** its own PostgreSQL database `tracking_click` with its own login, never another module's
  (architecture D16). `database/TrackingClickDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/tracking-click/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(TrackingClickDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(TrackingClickDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.
