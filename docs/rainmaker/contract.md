# Rainmaker demo: the contract between content, matching and reader

**Status:** agreed by the team on 21 Oct 2026 (#145). **Demo:** Fri 30 Oct 2026.
**Owner of this file:** the whole team. Changes: see [section 10](#10-changing-this-file).

## 0. In one minute

Three people build three parts at the same time, in three apps. This file fixes what each part
hands to the next, so everyone can start with fake data that looks exactly like the real thing.

```mermaid
sequenceDiagram
    participant Site as Vintrabladet (:3000)
    participant G2 as G2 Content & AI (:8082)
    participant G3 as G3 Publisher & offers (:8083)
    participant G1 as G1 Reader & clicks (:8081)
    participant JS as rainmaker.js
    Site->>G2: RSS item
    G2->>G2: store, classify (Claude), match
    G2->>G3: GET /api/offers (matching needs the offers)
    G3->>G2: GET /api/articles?recent=… (polls the recent articles and suggestions)
    G3->>G3: editor approves
    JS->>G1: GET /widget/v1/links?url=… (after qualified reading; hover in the demo, ADR 0005)
    G1->>G3: GET /api/approved-links?url=…
    G3->>G2: GET /api/articles?url=… (is it still green?)
    G1-->>JS: links with signed click URLs
    JS->>G1: reader clicks /c/{token}
    G1->>G1: check the signature, store the click
    G1-->>JS: 302 to the offer's page
```

| Part | App | Issues | Sections to read |
|---|---|---|---|
| Content | G2 | #147, #148 | 1, 2, 3, 6.1, 7.1, 8 |
| Matching & editor | G2 (matching), G3 (offers, editor) | #149, #150 | 1, 2, 3, 4, 5, 6.2, 7.1–7.3, 7.6 |
| Reader & clicks | G1 | #151, #152, #153 | 1, 2, 5, 7.3–7.5 |
| Skeleton, local run, E2E | all | #155, #146, #154 | 1, 7, 9 |

### Decisions

- [x] **D-0** The shape of this draft: the objects ([section 3](#3-the-data-each-part-produces)), the API paths ([section 7](#7-rest-between-the-apps)) and the signed click link with `clickId` ([section 7.5](#75-click-link-g1-153-d-6)). Agreed 21 Oct 2026.
- [x] **D-1** Three apps, G1, G2 and G3, on ports 8081–8083. Decided 21 Oct 2026 ([section 1](#1-apps-and-modules-d-1)).
- [x] **D-2** An article is recognised by its canonical URL. Agreed 21 Oct 2026 ([section 2](#2-how-an-article-is-recognised-d-2)).
- [x] **D-3** Topics are a fixed list of commercial categories, shared with offers; not the site's sections. Agreed 21 Oct 2026 ([section 3.2](#32-classification-g2-content-intent)).
- [x] **D-4** An unclassified or failed article is stored as its own state, not as red, but treated exactly like red: no suggestions, no links, no panel. Agreed 21 Oct 2026 ([section 3.3](#33-zone-rules-d-4)).
- [x] **D-5** A new article version resets its suggestions; the editor approves again. Agreed 21 Oct 2026 ([section 5](#5-suggestion-lifecycle-d-5)).
- [x] **D-6** `clickId` is created when the link is served and lives inside the signed token, with the destination and an expiry, so a click needs no other app. Agreed 21 Oct 2026; destination and expiry added after review ([section 7.5](#75-click-link-g1-153-d-6)).
- [x] **D-7** G1 serves both rainmaker.js and the widget API, from one origin. Agreed 21 Oct 2026 ([section 7.4](#74-widget-api-g1-151-d-7)).
- [x] **D-8** We reach Claude through Amazon Bedrock. Decided 21 Oct 2026 ([section 8](#8-reaching-claude-g2-148-d-8)).
- [x] **D-9** Claude writes its `reason` in both Swedish and English; the editor view shows the one for the chosen language. Agreed 21 Oct 2026 ([section 3.2](#32-classification-g2-content-intent)).

---

## 1. Apps and modules (D-1)

**Decision D-1: three apps,** as in the architecture's groups and the root `CLAUDE.md`. Module
folders and packages follow `CLAUDE.md`. Each module owns its own database (Flyway) and exposes
only its `api` package. Inside an app, modules talk through `api` interfaces and Spring events
(section 6); between apps, through REST (section 7).

| App (`apps/…`) | Port | Modules (`services/…`) | Owns (its own database) | Filled by |
|---|---|---|---|---|
| G1 `g1-reader-app` | 8081 | `widget-delivery` (S1) | nothing | #151 |
| | | `tracking-click` (S2) | clicks (append-only) | #153 |
| | | serves `widget/` (rainmaker.js) | – | #152 |
| G2 `g2-content-app` | 8082 | `content-ingestion` (S5) | articles and their versions | #147 |
| | | `content-intent` (S6) | classifications | #148 |
| | | `matching-ranking` (S7) | the latest suggestions per article | #149 |
| G3 `g3-publisher-app` | 8083 | `publisher-control` (S3) | editor decisions, audit log, editor page | #150 |
| | | `advertiser-marketplace` (S4) | offers, landing pages | #149 |

Packages: `se.rainmaker.<folder without dashes>`, e.g. `se.rainmaker.matchingranking`.

Ports: Vintrabladet **3000**, G1 **8081**, G2 **8082**, G3 **8083**, PostgreSQL **5432** (one
server, one database per module; started by #146).

**Between the groups, the architecture uses EventBridge + SQS for events.** AWS is "not now"
for the demo, so G3 polls G2 instead (section 7.1). This departs from `CLAUDE.md` and is
recorded in [ADR 0004](../adr/0004-rainmaker-demo-three-apps-over-rest.md).

---

## 2. How an article is recognised (D-2)

**Decision D-2: by its canonical URL,** exactly as the RSS `<link>` gives it, for example
`http://localhost:3000/artikel/badrumsrenovering-experternas-rad`.

- G2 stores it as `url`. It never changes when the article is edited (#159 keeps the slug).
- rainmaker.js reads `<link rel="canonical">`; without one, it uses
  `location.origin + location.pathname`, with no query string and no `#…`. Every page on the
  fake-news site has the canonical link, with the same absolute URL as the RSS `<link>`.
- Every app compares the URL as-is: no lower-casing and no trailing-slash games. The
  fake-news site already sends one consistent form.
- Rainmaker's own id for the article is `articleId`. The site's id (`vb-0006`) is kept as
  `externalId`, for debugging only. Real publishers won't have it, so nothing may depend on it.

---

## 3. The data each part produces

Shared rules for every object, event and API:

- Times are ISO 8601 in UTC with `Z`, e.g. `"2026-10-30T09:15:00Z"`.
- Ids are UUIDv7 (time-ordered). The examples shorten them for readability. Java's
  `UUID.randomUUID()` gives v4, so we write a small v7 generator ourselves: no new dependency.
- Money is a whole number of öre: `"cpcOre": 450` = 4.50 kr. Never a decimal number. The demo
  only uses SEK.
- Every article gets a `correlationId` at ingestion. It travels in every event, log line and
  REST call about that article (architecture 12.4). A click's correlation id is its `clickId`.

### 3.1 Article (G2: content-ingestion)

```json
{
  "articleId": "0192f3c2-…",
  "externalId": "vb-0006",
  "version": 1,
  "url": "http://localhost:3000/artikel/badrumsrenovering-experternas-rad",
  "headline": "Badrummet som håller i 40 år – …",
  "text": "Ett nytt badrum kostar ofta …",
  "section": "bostad",
  "language": "sv",
  "publishedAt": "2026-10-10T05:45:00Z",
  "updatedAt": null,
  "correlationId": "c0a1…"
}
```

- `version` starts at 1. It goes up by one each time the site shows a newer `updatedAt` for
  the same URL (#147). The RSS feed has it as `<atom:updated>` per item (#159), like the news
  sitemap's `<lastmod>`: the last change, or the publish time if the article was never
  updated.
- `text` is the full body text, without the headline. It stays inside G2: never logged, never
  in a REST answer.

### 3.2 Classification (G2: content-intent)

```json
{
  "articleId": "0192f3c2-…",
  "articleVersion": 1,
  "status": "classified",
  "zone": "green",
  "topics": ["home"],
  "reason": {
    "sv": "Praktiska råd om badrumsrenovering; tydlig köpavsikt, inget känsligt.",
    "en": "Practical advice on renovating a bathroom; clear buying intent, nothing sensitive."
  },
  "model": "claude-haiku-5-5",
  "classifiedAt": "2026-10-30T09:15:04Z",
  "correlationId": "c0a1…"
}
```

- `status` is `unclassified` (stored, not classified yet), `classified` or `failed`. `zone` and
  `reason` are `null` and `topics` is empty unless `status` is `classified`. See section 3.3.
- `model` is the model id from the settings, exactly as sent to Bedrock (section 8). The
  examples use the short name `claude-haiku-5-5`; the real value is the Bedrock id from #161.

**Decision D-3: topics are commercial categories,** the same list offers use as `category`.
Not the site's sections: a section says where an article sits, not what it is about. A story
in "nyheter" can be about travel, and "nyheter" can't be matched to an offer.

| Key | Example on Vintrabladet |
|---|---|
| `travel` | Mallorca in autumn |
| `outdoor` | Mountain hiking guide |
| `sport` | Football promotion, running race |
| `culture` | Theatre musical |
| `food` | Chanterelles and a recipe |
| `home` | Bathroom renovation |
| `cars` | Electric car test |
| `health` | Young people sleeping too little |
| `personal-finance` | Household savings |
| `other` | Nothing commercial fits (the accident, the robbery) |

- The keys are code values, not text. They live in content-intent's `api` package; the offers
  in G3 use the same values. Display names come from the sv/en translation files (`CLAUDE.md`).
- Claude picks 1–3 keys and never invents new ones: the JSON schema in section 8 only allows
  these values. No offer has the category `other`, so `other` never matches.
- Claude lists the topics **most relevant first**, and `topics` keeps that order: matching
  ranks by it (section 4).
- Crime, politics and accidents are not topics; the zone handles them.

**Decision D-9: `reason` in both languages.** The editor view runs in Swedish or English, so
Claude writes the reason in both in the same call, and the editor view shows the one for the
chosen language. An answer without both counts as `failed`.

### 3.3 Zone rules (D-4)

| `status` | `zone` | Meaning | Suggestions? | Panel? | Editor view shows |
|---|---|---|---|---|---|
| `classified` | `green` | Safe, with commercial intent | Yes (up to 3) | Yes, after approval | The zone and suggestions |
| `classified` | `yellow` | Sensitive (health, personal finance) | No, for the demo | No | Yellow |
| `classified` | `red` | Never next to ads (crime, tragedy) | No, never | No, never | Red |
| `unclassified` | `null` | Stored, not classified yet | No | No | "Not classified yet" |
| `failed` | `null` | Timeout, refusal, invalid answer, retries used up | No | No | "Couldn't be classified" |

**Decision D-4:** `unclassified` and `failed` are stored as their own states, never as red,
so the editor view tells the truth, the failure counts stay honest, and a later retry can
classify the article properly. Everywhere else they are treated exactly like red: no
suggestions, no links from any API, no panel. Never green by default.

Rule for #148: a few retries with back-off, then `failed`.

### 3.4 Offer (G3: advertiser-marketplace)

```json
{
  "offerId": "0192f3d0-…",
  "advertiser": "Vintra Kakel & Bad",
  "title": "Gratis hembesök för badrumsoffert",
  "text": "Vi mäter upp och ger fast pris inom en vecka.",
  "category": "home",
  "destinationUrl": "http://localhost:8083/landing/0192f3d0-…",
  "cpcOre": 450
}
```

- 5–10 fictional offers, loaded by a Flyway migration (#149). Each `destinationUrl` points to
  its own fictional landing page in G3.
- `destinationUrl` travels from G3 to G1 in the approved links (section 7.3). G1 puts it only
  inside the signed click link (section 7.5), never as a field in the widget API. The link is
  signed, not encrypted: anyone can decode it, but nobody can change it.

### 3.5 Suggestion (G2: matching-ranking)

```json
{
  "suggestionId": "0192f3e4-…",
  "articleId": "0192f3c2-…",
  "articleVersion": 1,
  "offerId": "0192f3d0-…",
  "rank": 1,
  "matchedTopics": ["home"],
  "status": "suggested"
}
```

- G2 makes the suggestions. G3 keeps the editor's decision: `status` is `suggested` |
  `approved` | `rejected` | `withdrawn` (section 5). `withdrawn` means the article changed or
  stopped being green.
- `matchedTopics` says why the offer fits. The editor view turns it into a sentence from the
  translation files ("Matches the article's topic: Home"), so the suggestion needs no text of
  its own in either language.

### 3.6 Click (G1: tracking-click)

```json
{
  "clickId": "0192f3f8-…",
  "articleId": "0192f3c2-…",
  "offerId": "0192f3d0-…",
  "clickedAt": "2026-10-30T10:02:11Z"
}
```

Append-only. `clickId` is unique in the database (#153). No IP, no cookie, no user id.

---

## 4. Matching rules (G2: matching-ranking)

- Only `classified` + `green` articles get suggestions. Everything else gets an empty set (D-4).
- An offer fits when its `category` is one of the article's `topics`. The earlier the topic in
  the list, the better the fit: an offer for the first topic beats one for the second, whatever
  the CPC (decided in #149).
- At most 3 suggestions, ordered by fit. **CPC only breaks ties** (product rule), then the offer
  id, so the order never changes.
- An offer whose `category` isn't a known topic key is skipped and logged, never an error: the
  keys are kept in both G3 and G2.
- Matching reads the offers from G3 (section 7.2), with timeouts and retries. If G3 still can't
  be reached, nothing new is stored and the error is logged; a new version still loses its old
  set (D-5). The article is matched again the next time it is classified (decided in #149).
- Not green never waits for G3: the set is emptied at once.

---

## 5. Suggestion lifecycle (D-5)

```
        G2 sends a set              editor (G3)
 (none) ──────────────▶ suggested ──────▶ approved
                            │      └────▶ rejected
                            │
  new article version, or   ▼
  article not green ──▶ withdrawn  (from any status)
```

**Decision D-5:** a new article version replaces the whole set. Every earlier suggestion for
that article becomes `withdrawn`, even an approved one, and the editor approves again. The
text changed, so the old approval no longer covers it. The demo's "edited into a red article"
case (#159) then withdraws everything, as #149 and #151 require.

A link reaches the widget only when **all** of these hold:

1. the suggestion is `approved` in G3;
2. it belongs to the article's latest version;
3. G2 says, at that moment, that this version is `classified` + `green` (section 7.3).

---

## 6. Events inside an app (Spring events)

Each event is a Java record in the publishing module's `api` package. Listeners run after the
publisher's transaction commits (`@TransactionalEventListener`), and **must be idempotent**:
the same event twice changes nothing.

### 6.1 Inside G2: ArticleStored (content-ingestion → content-intent)

Published for a new article and for every new version. content-intent stores the
classification as `unclassified` at once, then classifies.

```json
{
  "articleId": "0192f3c2-…",
  "version": 2,
  "url": "http://localhost:3000/artikel/badrumsrenovering-experternas-rad",
  "headline": "Badrummet som håller i 40 år – …",
  "text": "…",
  "section": "bostad",
  "language": "sv",
  "publishedAt": "2026-10-10T05:45:00Z",
  "updatedAt": "2026-10-30T09:40:00Z",
  "correlationId": "c0a1…"
}
```

The event carries the text, so content-intent never has to read content-ingestion's database.

### 6.2 Inside G2: ArticleClassified (content-intent → matching-ranking)

The classification from 3.2, also published when `status` is `failed`. A listener ignores it
if `articleVersion` is older than the newest version it has seen. matching-ranking then stores
the complete set of suggestions for that version (an empty set when the article is not green).

**Open point (outbox):** `CLAUDE.md` asks for the outbox pattern. For the demo, an event lost
in a crash is accepted: restarting ingestion re-reads the feed. A real outbox (for example
Spring Modulith's event publication registry) is a new dependency, so it is a follow-up issue
after the demo.

---

## 7. REST between the apps

Rules for every call between the apps:

- JSON, with the times, ids and money rules from section 3.
- The caller sends `X-Correlation-Id` (the article's `correlationId`, or the `clickId`).
- Short timeouts and retries with back-off, and circuit breakers (Resilience4j), as `CLAUDE.md`
  requires for external calls. All of them are settings.
- No login between the apps: the internal APIs (`/api/…`) are for localhost only in the demo.
- **Start in any order.** G2 needs G3's offers and G3 polls G2, so each app must start, and keep
  retrying, while the other one isn't up yet (#146).
- **Fail closed for brand safety:** when a call that decides whether links may be shown fails,
  the answer is "no links", never the last known answer.
- **Contract tests:** each JSON example in this section becomes a file in
  `docs/rainmaker/contract/` that both sides' tests read, so the two sides can't drift.

| Call | From → to | Section |
|---|---|---|
| `GET /api/articles?recent=…` and `?url=…` | G3 → G2 | 7.1 |
| `GET /api/offers` | G2 → G3 | 7.2 |
| `GET /api/approved-links?url=…` | G1 → G3 | 7.3 |
| `GET /widget/v1/links?url=…` | rainmaker.js → G1 | 7.4 |
| `GET /c/{token}` | reader's browser → G1 | 7.5 |

### 7.1 Articles API (G2, #202)

```
GET http://localhost:8082/api/articles?recent=100
GET http://localhost:8082/api/articles?url=http%3A%2F%2Flocalhost%3A3000%2Fartikel%2Fbadrumsrenovering-experternas-rad
```

- `recent`: a snapshot of the latest version of the `recent` (1–500) articles whose **latest
  version was stored most recently** (content-ingestion's `stored_at`), newest first, each with
  its current classification and suggestions. An edited article moves back to the top. G3 polls
  it (every few seconds, a setting) and applies the whole answer every time; applying the same
  article again changes nothing.
- `url`: the latest version of that one article, or an empty list. G3 uses it on every widget
  request to check the current zone (section 7.3).
- Exactly one of `recent` and `url` is required, and `recent` must be a whole number from 1 to
  500; otherwise `400`.
- **Why a snapshot, not "changed since":** a snapshot needs no module to record when its part of
  an article changed (a reclassification changes only content-intent, a new set only matching).
  After the demo, events replace the polling (ADR 0004). Each event then carries one article
  object exactly as below, so G3 handles both the same way, and `recent` stays for filling G3's
  copy from scratch.

`200 OK`:

```json
{
  "articles": [
    {
      "articleId": "0192f3c2-…",
      "externalId": "vb-0006",
      "version": 1,
      "url": "http://localhost:3000/artikel/badrumsrenovering-experternas-rad",
      "headline": "Badrummet som håller i 40 år – …",
      "section": "bostad",
      "language": "sv",
      "publishedAt": "2026-10-10T05:45:00Z",
      "updatedAt": null,
      "classification": {
        "status": "classified",
        "zone": "green",
        "topics": ["home"],
        "reason": {
          "sv": "Praktiska råd om badrumsrenovering; tydlig köpavsikt, inget känsligt.",
          "en": "Practical advice on renovating a bathroom; clear buying intent, nothing sensitive."
        },
        "model": "claude-haiku-5-5",
        "classifiedAt": "2026-10-30T09:15:04Z"
      },
      "suggestions": [
        {"suggestionId": "0192f3e4-…", "offerId": "0192f3d0-…", "rank": 1, "matchedTopics": ["home"]}
      ],
      "correlationId": "c0a1…"
    }
  ]
}
```

- `suggestions` is always the **complete current set** for that version. An empty list means
  "no suggestions", so G3 withdraws the earlier ones (D-5).
- No `text`: the body stays in G2.

### 7.2 Offers API (G3, #149)

```
GET http://localhost:8083/api/offers
```

`200 OK`:

```json
{
  "offers": [
    {
      "offerId": "0192f3d0-…",
      "advertiser": "Vintra Kakel & Bad",
      "title": "Gratis hembesök för badrumsoffert",
      "text": "Vi mäter upp och ger fast pris inom en vecka.",
      "category": "home",
      "destinationUrl": "http://localhost:8083/landing/0192f3d0-…",
      "cpcOre": 450
    }
  ]
}
```

G2 reads the list for matching.

### 7.3 Approved links API (G3, #150)

```
GET http://localhost:8083/api/approved-links?url=http%3A%2F%2Flocalhost%3A3000%2Fartikel%2Fbadrumsrenovering-experternas-rad
```

`200 OK`:

```json
{
  "articleId": "0192f3c2-…",
  "articleUrl": "http://localhost:3000/artikel/badrumsrenovering-experternas-rad",
  "links": [
    {
      "offerId": "0192f3d0-…",
      "rank": 1,
      "advertiser": "Vintra Kakel & Bad",
      "title": "Gratis hembesök för badrumsoffert",
      "text": "Vi mäter upp och ger fast pris inom en vecka.",
      "destinationUrl": "http://localhost:8083/landing/0192f3d0-…"
    }
  ]
}
```

- G3 applies the three rules in section 5. For rule 3 it asks G2 (`/api/articles?url=…`)
  on every call.
- An unknown article: `"articleId": null` and `"links": []`. Any article that is not green, or
  has no approved suggestions: `"links": []`.
- If G2 can't be reached: `503`, never links (fail closed).
- `destinationUrl` is for G1 to put into the signed click link (section 7.5). It never goes to
  the browser as a field.

**Known limit:** each call from rainmaker.js is G1 → G3 → G2, three hops. Fine for the demo;
caching is out of scope.

### 7.4 Widget API (G1, #151) (D-7)

```
GET http://localhost:8081/widget/v1/links?url=http%3A%2F%2Flocalhost%3A3000%2Fartikel%2Fbadrumsrenovering-experternas-rad
```

`200 OK`, `Content-Type: application/json`, `Cache-Control: no-store`:

```json
{
  "articleUrl": "http://localhost:3000/artikel/badrumsrenovering-experternas-rad",
  "links": [
    {
      "offerId": "0192f3d0-…",
      "advertiser": "Vintra Kakel & Bad",
      "title": "Gratis hembesök för badrumsoffert",
      "text": "Vi mäter upp och ger fast pris inom en vecka.",
      "clickUrl": "http://localhost:8081/c/eyJjIjoi….Q2hN…"
    }
  ]
}
```

- **Empty, not an error:** for a red, yellow, failed, unclassified or unknown article, or one
  with no approved links, the API returns 200 with `"links": []`.
- `400` when `url` is missing or not an http(s) URL. `503` when G3 can't answer.
- rainmaker.js shows a panel only for a 200 with links. Anything else, including a timeout,
  means no panel, and the page works as before (fail open for the reader, #152).
- At most 3 links, in rank order. No `destinationUrl`, ever.
- CORS: `Access-Control-Allow-Origin: http://localhost:3000` (a setting). No cookies, and
  nothing about the reader is stored or logged.
- The "Annons" label and other panel text come from rainmaker.js's translation file, not from
  this API (#152).

**Decision D-7:** G1 also serves rainmaker.js at `http://localhost:8081/widget/rainmaker.js`.
Start the fake-news site with `RAINMAKER_SCRIPT_URL=http://localhost:8081/widget/rainmaker.js`.
Its Content-Security-Policy then allows the one origin `http://localhost:8081`: the site's
`SecurityHeadersFilter` already adds the widget's origin to both `script-src` and
`connect-src`, so the script and its API call both load.

### 7.5 Click link (G1, #153) (D-6)

```
GET http://localhost:8081/c/{payload}.{signature}
```

- `payload` = base64url of
  `{"c":"<clickId>","a":"<articleId>","o":"<offerId>","d":"<destinationUrl>","exp":1793440931}`.
  `exp` is when the link stops working, in Unix seconds (an exception to section 3, to keep
  the link short): the time the link was served plus 24 hours (a setting).
- `signature` = base64url HMAC-SHA256 of the payload. The key comes from the environment
  (`RAINMAKER_CLICK_SIGNING_KEY`), never from code.

**Decision D-6:** G1 makes a new link, with a new `clickId`, each time it answers the widget.
So a double-click or a retry of the same link carries the same `clickId` and is stored once. A
page reload gives a new link, and that is a new click (the 30-minute dedupe is "not now").

The destination is inside the signed link, so G1 redirects and stores the click on its own:
the money path doesn't depend on G3, and the redirect needs no network call (the
architecture's target is under 100 ms). `exp` limits how long a copied link can be replayed.

Opening the link:

| Case | Answer | Stored? |
|---|---|---|
| Valid signature, not expired | `302 Found`, `Location`: the `d` from the link | One row per `clickId` |
| Same `clickId` again | `302` to the same place | No new row |
| Bad, changed or garbled token | `400` error page | Nothing |
| Valid signature, expired | `410 Gone` error page | Nothing |

- The redirect target comes from the signed link, which only G1 can make, never from anything
  the request can change (no open redirect).
- **Trade-off:** an offer removed after its link was served still redirects until the link
  expires.
- The error pages' text comes from G1's sv/en translation files (`CLAUDE.md`), e.g. "Länken är
  ogiltig" / "The link is invalid".

### 7.6 Editor page (G3, #150)

An internal UI, under `/publisher/` so the paths don't clash with the fake-news site's own
`/editor` (#159):

| Method | Path | What |
|---|---|---|
| GET | `/publisher/articles` | Recent articles: headline, zone (or "Not classified yet" / "Couldn't be classified"), reason, suggestions |
| POST | `/publisher/suggestions/{suggestionId}/approve` | Approve; writes an audit row |
| POST | `/publisher/suggestions/{suggestionId}/reject` | Reject; writes an audit row |

The page states that there's no login (local demo only). An audit row (append-only) looks
like this:

```json
{"auditId": "…", "suggestionId": "0192f3e4-…", "action": "approved", "by": "demo-editor", "at": "2026-10-30T09:20:00Z"}
```

`action` is `approved` or `rejected` by the editor, or `withdrawn` by `g2` when G3 withdraws a
suggestion because of G2's data (section 5), so an approved link leaving the widget is logged too.

---

## 8. Reaching Claude (G2, #148) (D-8)

**Decision D-8 (21 Oct 2026): Amazon Bedrock,** as in the target architecture. AWS access is
set up in #161: the dev account, the G2 role and local SSO login. No Anthropic API key is used,
and no key is stored anywhere: the AWS SDK picks up the SSO login locally.

- content-intent classifies through one `ArticleClassifier` interface (the name #148 uses).
  The Bedrock call is one implementation of it. Tests use a fake `ArticleClassifier` (or
  WireMock), never Bedrock (`CLAUDE.md`: external APIs are mocked).
- The model id and the region are settings. Their exact values come from #161: use the Bedrock
  model id, or the inference profile, that #161's role is allowed to call.
- Model: Claude Haiku 5.5. Check it against the site's `/eval/labels.csv` by hand (#148); if it
  misses yellow or red articles, try Sonnet 5.5.
- The call has a timeout and retries with back-off. When they are used up, the classification
  is `failed` (section 3.3).

Answer format (structured output; anything else counts as `failed`):

```json
{
  "type": "object",
  "required": ["zone", "topics", "reason"],
  "additionalProperties": false,
  "properties": {
    "zone": {"enum": ["green", "yellow", "red"]},
    "topics": {
      "type": "array",
      "minItems": 1,
      "maxItems": 3,
      "items": {"enum": ["travel", "outdoor", "sport", "culture", "food", "home", "cars", "health", "personal-finance", "other"]}
    },
    "reason": {
      "type": "object",
      "required": ["sv", "en"],
      "additionalProperties": false,
      "properties": {
        "sv": {"type": "string", "maxLength": 300},
        "en": {"type": "string", "maxLength": 300}
      }
    }
  }
}
```

Structured-output modes don't always support every JSON Schema keyword (`maxLength`,
`minItems` and `maxItems` here). If Bedrock rejects them, leave them out of the request,
check the same limits in code, and treat a violation as `failed`.

The prompt says that the article is data, never instructions. Only the headline and text are
sent. Nothing about readers is ever sent.

---

## 9. How the demo's "done means" map to this file (#154)

| Done means (handledare, 20 Oct) | Covered by |
|---|---|
| A new fake article shows up in the editor view | 6.1 → 6.2 → 7.1 → 7.6 |
| A red article never shows a panel | 3.3, 5, 7.3, 7.4 |
| After approval, the panel shows the links | 5, 7.3, 7.4 |
| If Rainmaker is down, the article page still loads normally | 7.4 (rainmaker.js fails open, #152) |
| A click lands on the right page and is stored once | 7.5 |

## 10. Changing this file

Any change to a shape in sections 3, 6 or 7 goes through a PR that tags all three parts. A
field may be added freely. Renaming or removing one needs everyone's OK, because the contract
tests will fail otherwise (`CLAUDE.md`, "Contract tests").
