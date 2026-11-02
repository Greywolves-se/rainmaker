# rainmaker.js (`widget/`)

The script a publisher adds to its pages with one tag:

```html
<script async src="http://localhost:8081/widget/rainmaker.js"></script>
```

First the article, then the commercial next step
([ADR 0005](../docs/adr/0005-behaviour-based-reader-flow.md)): once the reader has read the
article (#218) and reached its end (#219), it shows the links the editor approved for that
article in a small box labelled "Annons", beside the article on desktop and after the article
text on mobile (#220). An article without approved links gets nothing: no box, no marker. The
headline hover from #152 is gone.

How it works:
1. It reads its settings from its own `<script>` tag: the widget API is on the origin the script
   was loaded from (contract, D-7); the other settings are below.
2. It waits for the leaving signal (`src/leaving-article.ts`). A page view that never gets there
   makes no call.
3. Then it asks G1's widget API once for the article's links (`GET /widget/v1/links?url=…`,
   contract 7.4), using the article's canonical URL (D-2). If the call fails (an error, 2 s
   without an answer or a status other than 200), nothing shows in this page view.
4. It shows at most 3 links in the place for the next step (below). Each one is G1's click link;
   a link pointing anywhere else is dropped, so an advertiser's address never reaches the page
   directly.

## The next step (#220)

- **Two places, marked by the publisher** (test site: #217). The widget fills one of them and
  never adds anything elsewhere:

  | Place | Default selector | Script tag setting |
  |---|---|---|
  | Beside the article (desktop) | `[data-rainmaker-next-step="sidebar"]` | `data-sidebar-place-selector` |
  | After the article text (mobile) | `[data-rainmaker-next-step="after-article"]` | `data-after-article-place-selector` |

- **The layout decides, not a screen width:** the place beside the article is used when its
  column doesn't overlap the article text horizontally, measured when the next step shows. So the
  publisher's own breakpoints are followed (the test site switches at 960 px). Otherwise it goes
  after the article text; a page with only the sidebar place gets it there, below the article.
- **Never covers the article:** the box goes into the place, in the page's flow, and takes the
  place's width. Nothing is positioned over the page. On desktop the place must be in view when
  the reader reaches the end; the test site makes it sticky (`test-site/README.md`, #217).
- **No close button** for now: the box covers nothing. Whether readers want one is tested in
  #222.
- **Nothing without a place or a marker:** on a page without the end marker, or without either
  place, the widget doesn't watch the reading and makes no call.

## Qualified reading (#218)

The new reader flow (ADR 0005) shows the next step only after **qualified reading**: the reader
has read long enough for the interest to count as confirmed. `src/qualified-reading.ts` reports
it once per page view, to the leaving signal below.

- **Active time:** 60 seconds by default. Time counts only while the page is **visible** (the
  Page Visibility API: another tab or a locked phone doesn't count) and the reader has been
  **active** within the last 30 seconds: scrolling, a key, a tap or moving the pointer. Opening
  the page counts as activity, so the first seconds of reading count too.
- **And some scrolling:** without at least one scroll, the reader never qualifies, however long
  the page is open. A page left on the table stops adding time after 30 seconds and never
  scrolls.
- **The threshold is a setting:** `data-qualified-reading-seconds="90"` on the script tag. A whole
  number from 1 to 3600; anything else, or no attribute, means 60. Later it can come from the
  publisher's settings in S3.
- **Nothing leaves the browser:** no cookie, no `localStorage`, no network call. The counts live
  in the page view and are gone when it ends (no reader profiles; consent).
- **Open for #222:** the 30 seconds before the reader counts as idle, and whether a part of the
  article (for example `data-rainmaker-article-end`, #217) must also be reached. Both are
  constants or settings, so changing them is a small change.

## Leaving the article (#219)

The reader flow (ADR 0005) shows the next step when the reader has read the article and shows
signs of leaving it. `src/leaving-article.ts` gives that moment: `watchLeavingArticle` calls back
once per page view, when the reading is qualified (#218) **and** the reader is at or past the end
of the article, in either order. Then the next step shows (#220, above).

**The proposal** (for Peter, in the demonstration of both flows, #222). Candidate signals:

| Signal | Cost | When it is wrong |
|---|---|---|
| **The end-of-article marker is on the screen or above it** (chosen) | An `IntersectionObserver`; no scroll listener, no layout reads. Same on desktop and mobile. | A short article whose end is on the screen at once signals the moment the reading qualifies; after 60 s of reading and a scroll, that is fair. On a PLUS article the marker comes after the paywall offer (#217), so a reader who can't read the locked part still reaches it: whether to show anything there is open (#222). |
| Scrolling past the article body, towards the rest of the page | Similar. | Later than the marker: on mobile the place after the article may already have scrolled away. A page with nothing below the article can't be scrolled past, so it never fires. |
| Navigating away (`pagehide`, back, a link) | Low. | Nothing shown while the page unloads can be seen, so it is only useful for measurement, and this signal stores and sends nothing. An "exit intent" (the pointer moving to the address bar) exists only on desktop. |

- **"At or past":** the observed area is the screen stretched far upwards
  (`rootMargin: 1000000px 0px 0px 0px`). So the end counts as reached while it is on the screen
  *and* after the reader scrolled past it, even with a jump (the End key) that never shows it; a
  plain observer misses that jump. Checked in Chrome. A reader who scrolls back up above the end
  before the reading qualifies gets the next step when they reach the end again, not while
  re-reading.
- **A reader who never leaves:** one who stops at the end has reached it, so it fires. One who
  stops reading in the middle never gets the next step: nothing commercial, and nothing disturbs
  the reading.
- **The marker:** `[data-rainmaker-article-end]`, last in the article text (#217). A publisher
  with other markup adds `data-article-end-selector="…"` to the script tag. Without the marker,
  or in a browser without `IntersectionObserver`, there is no signal and nothing shows.
- **Open until #222:** whether a short article should wait for some scrolling past the end, and
  the PLUS case above.

## Rules it must keep

- **Fail open** (CLAUDE.md, product rules): if anything in Rainmaker fails, the publisher's page
  loads and works as before. Errors are caught in three places: `src/main.ts` while the script
  loads, `failOpen` (`src/fail-open.ts`) around every event handler and timer, and
  `fetchApprovedLinks` for the API call (an error, a non-200 answer or 2 s without an answer
  means no links). Tests check each one. Errors are dropped silently for now; reporting them is
  #175.
- **Isolated from the page, as far as a script can be:** the box's content lives in a Shadow
  DOM, so the page's CSS doesn't reach it. The host is its own element, `<rainmaker-panel>`, so
  page rules for ordinary elements (`div { … }`) don't match it; rules that match every element
  (`body > * { … }`) still could. Its CSS is a constructed stylesheet, which a Content-Security-Policy with
  `style-src 'self'` (like the fake-news site's) allows.
- **Quiet:** the widget never writes to the reader's console. The browser itself still logs a
  request that fails (for example when the API is down); a script can't prevent that.
- **Texts from translation files:** `src/translations/sv.json` and `en.json`, chosen by
  `<html lang>`.
- **Small:** at most 15 KB gzipped (architecture, acceptance criteria for A1). `npm run build`
  fails above that (`scripts/check-size.mjs`).
- **No traces on the page:** the build is one immediately invoked function, so it adds no
  global variables. Never export anything from `src/main.ts`: an export becomes a global. No
  framework, no separate CSS file.
- **No reader data:** no cookies, nothing about the reader stored or sent (contract, 7.4).

## Commands

Run them in `widget/` (Node.js 22 or newer):

| Command | What it does |
|---|---|
| `npm install` | Install the tools (TypeScript, gts, Vite, Vitest), the same versions as Mission Control's web app |
| `npm test` | Run the tests (Vitest, with a browser-like DOM from jsdom) |
| `npm run lint` | Google TypeScript style (gts); `npm run fix` fixes what it can |
| `npm run build` | Typecheck, build `dist/rainmaker.js` (one file, IIFE) and check its size |

`dist/` is git-ignored. G1 serves the built file at `http://localhost:8081/widget/rainmaker.js`
(contract, D-7; #178), read from `widget/dist/` on every request: after `npm run build`, reload the
page. See G1's README for how to start it.

## Files

| File | What |
|---|---|
| `src/main.ts` | The entry point: starts the widget and never lets an error reach the page |
| `src/start.ts` | Finds the end marker and the places, and connects the leaving signal, the API and the box |
| `src/page.ts` | Reads the settings from the script tag, and the article's URL (D-2) |
| `src/leaving-article.ts` | Signals once when the reader has read the article and reached its end (#219) |
| `src/links-api.ts` | Calls the widget API and checks its answer |
| `src/next-step-place.ts` | Chooses the place beside or after the article, by layout (#220) |
| `src/panel.ts` | Builds the next step's box in a Shadow DOM |
| `src/qualified-reading.ts` | Reports once when the reader has read long and actively enough (#218) |
| `src/fail-open.ts` | Keeps errors in event handlers and timers away from the page |
| `src/translations.ts`, `src/translations/` | The panel's texts, Swedish and English |
| `vite.config.ts` | Builds the single script, and configures Vitest |
| `scripts/check-size.mjs` | The 15 KB gzipped budget |
