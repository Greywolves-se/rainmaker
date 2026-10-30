# rainmaker.js (`widget/`)

The script a publisher adds to its pages with one tag:

```html
<script async src="http://localhost:8081/widget/rainmaker.js"></script>
```

When a reader hovers a headline for at least 300 ms, it shows a small panel labelled "Annons"
with the links the editor approved for that article (#152). It closes when the pointer has left
both the headline and the panel. An article without approved links gets nothing: no panel, no
marker.

> **Being replaced:** the hover is what the demo uses. A behaviour-based reader flow replaces it
> ([ADR 0005](../docs/adr/0005-behaviour-based-reader-flow.md)): the links appear after qualified
> reading, beside the article on desktop and after it on mobile (#217–#220). This README describes
> the code as it is until #220 lands.

How it works:
1. It reads its settings from its own `<script>` tag: the widget API is on the origin the script
   was loaded from (contract, D-7), and the headline is `[data-rainmaker-headline]`. A publisher
   with other markup adds `data-headline-selector="h1.title"` to the tag.
2. On the first hover it asks G1's widget API once for the article's links
   (`GET /widget/v1/links?url=…`, contract 7.4), using the article's canonical URL (D-2), and
   remembers the answer for the page view. If the call failed (an error, 2 s without an answer or
   a status other than 200), the next hover asks again.
3. It shows at most 3 links. Each one is G1's click link; a link pointing anywhere else is
   dropped, so an advertiser's address never reaches the page directly.

## Rules it must keep

- **Fail open** (CLAUDE.md, product rules): if anything in Rainmaker fails, the publisher's page
  loads and works as before. Errors are caught in three places: `src/main.ts` while the script
  loads, `failOpen` (`src/fail-open.ts`) around every event handler and timer, and
  `fetchApprovedLinks` for the API call (an error, a non-200 answer or 2 s without an answer
  means no links). Tests check each one. Errors are dropped silently for now; reporting them is
  #175.
- **Isolated from the page, as far as a script can be:** the panel's content lives in a Shadow
  DOM, so the page's CSS doesn't reach it, and its host is positioned absolutely, so nothing on
  the page moves. The host is its own element, `<rainmaker-panel>`, so page rules for ordinary
  elements (`div { … }`) don't match it; rules that match every element (`body > * { … }`) still
  could. Its CSS is a constructed stylesheet, which a Content-Security-Policy with
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
| `src/start.ts` | Finds the headline and connects the API, the hover and the panel |
| `src/page.ts` | Reads the settings from the script tag, and the article's URL (D-2) |
| `src/links-api.ts` | Calls the widget API and checks its answer |
| `src/hover-panel.ts` | Opens the panel after a deliberate hover and closes it again |
| `src/panel.ts` | Builds the panel in a Shadow DOM |
| `src/fail-open.ts` | Keeps errors in event handlers and timers away from the page |
| `src/translations.ts`, `src/translations/` | The panel's texts, Swedish and English |
| `vite.config.ts` | Builds the single script, and configures Vitest |
| `scripts/check-size.mjs` | The 15 KB gzipped budget |
