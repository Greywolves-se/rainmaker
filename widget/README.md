# rainmaker.js (`widget/`)

The script a publisher adds to its pages with one tag:

```html
<script async src="http://localhost:8081/widget/rainmaker.js"></script>
```

When a reader hovers a headline, it shows a small, labelled panel with the links the editor
approved for that article. The panel itself is built in #152. This folder was set up empty in
#155, so #152 only adds code.

## Rules it must keep

- **Fail open** (CLAUDE.md, product rules): if anything in Rainmaker fails, the publisher's page
  loads and works as before. `src/main.ts` catches everything, and a test checks it.
- **Small:** at most 15 KB gzipped (architecture, acceptance criteria for A1). `npm run build`
  fails above that (`scripts/check-size.mjs`).
- **No traces on the page:** the build is one immediately invoked function, so it adds no
  global variables. No framework, no separate CSS file.
- **No reader data:** no cookies, nothing about the reader stored or sent (contract, 7.4).

## Commands

Run them in `widget/` (Node.js 22 or newer):

| Command | What it does |
|---|---|
| `npm install` | Install the tools (TypeScript, gts, Vite, Vitest), the same versions as Mission Control's web app |
| `npm test` | Run the tests (Vitest, with a browser-like DOM from jsdom) |
| `npm run lint` | Google TypeScript style (gts); `npm run fix` fixes what it can |
| `npm run build` | Typecheck, build `dist/rainmaker.js` (one file, IIFE) and check its size |

`dist/` is git-ignored. G1 serves the built file at `/widget/rainmaker.js` (contract, D-7; #152).

## Files

| File | What |
|---|---|
| `src/main.ts` | The entry point: starts the widget and never lets an error reach the page |
| `src/start.ts` | Starts the widget. Empty until #152 |
| `vite.config.ts` | Builds the single script, and configures Vitest |
| `scripts/check-size.mjs` | The 15 KB gzipped budget |
