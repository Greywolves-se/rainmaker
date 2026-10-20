# Mission Control – web

The Mission Control dashboard: React + TypeScript, built with Vite (#8). It follows the
Mission Control design mockup: three pages (Overview, Development, Sources) and the
Navigator panel on the right.

The pages show the collector's snapshot of the selected project. Without one, local dev shows
fictional demo data under a "Sample data" banner. See [Where the data comes from](#where-the-data-comes-from).

## Contents

- [Run it](#run-it)
- [How the app is put together](#how-the-app-is-put-together)
- [Common changes](#common-changes)
- [Where the data comes from](#where-the-data-comes-from)
- [Current limitations](#current-limitations)

## Run it

Needs Node.js 22 or newer.

```
npm ci
npm run dev      # local dev server
npm run build    # type check + production build into dist/
npm run lint     # ESLint with the gts (Google TypeScript style) rules
npm run fix      # auto-fix lint and formatting
npm test         # Vitest + React Testing Library
npm run e2e      # Playwright smoke tests against dist/: run npm run build first
```

The first time, `npx playwright install chromium` downloads the browser the smoke tests use.

Two Windows pitfalls:

- **Keep the repository on a local disk, not in a Google Drive or OneDrive folder.**
  The sync client locks files while npm writes `node_modules`, and `npm install` fails
  with `EPERM` / `EBADF`.
- **If PowerShell says "running scripts is disabled"**, type `npm.cmd` instead of `npm`,
  or run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once.

## How the app is put together

### Startup

```
index.html  →  src/main.tsx  →  src/App.tsx (routes)  →  Layout  →  page
```

- `index.html` is an empty page with `<div id="root">`. React renders everything into it.
- `src/main.tsx` loads the CSS and renders `<App />` inside two providers:
  `I18nextProvider` (texts) and `BrowserRouter` (the address bar decides the page).
- `src/App.tsx` is the route table: `/` → Overview, `/development` → Development,
  `/sources` → Sources, anything else → back to `/`. All routes sit inside `Layout`.

### The frame around every page

`src/layout/Layout.tsx` draws the parts that never change:

```
┌──────────┬──────────────────────────────────────────┐
│ Sidebar  │ TopBar (search, time, language)          │
│          │ Page title + subtitle                    │
│ menu     ├───────────────────────────┬──────────────┤
│ projects │ <Outlet/> = current page  │  Navigator   │
│ sync box │                           │              │
└──────────┴───────────────────────────┴──────────────┘
```

- `<Outlet />` is where the router puts the current page.
- `src/layout/pages.ts` lists the pages once. The sidebar menu, the page title and the
  browser-tab title ("Development – Mission Control") are all built from it.

### Data flow

```
<project>/snapshot.json  →  snapshotClient (fetch + parseSnapshot)  →  fromContract()  →  layout/Layout (per project)
               →  useSnapshot()  →  Layout and the three pages  →  smaller components
```

- `src/data/types.ts` defines the page-shaped data (`Snapshot`). `null` or an empty list
  means the snapshot has no data for that part; the page then shows "No data yet".
- `src/data/fromContract.ts` is the only place that knows both the contract and the page
  shape. See [Where the data comes from](#where-the-data-comes-from).
- `src/data/useSnapshot.ts` is the **only** way components get data. No component fetches
  or imports data directly.
- Pages pass slices of the snapshot down as props, e.g. `OverviewPage` gives
  `overview.events` to `RecentEvents`.

### Text: labels vs data

- **Labels** (headings, buttons, status names) live in `src/i18n/sv.json` and `en.json`. Components
  read them by key: `t('overview.recentEvents')` → "Recent events". Placeholders are
  filled at runtime: `"Done · {{count}}"` + `{count: 4}` → "Done · 4". Never write UI
  text directly in a component (see the root `CLAUDE.md`).
- **Data** (story titles, names, notes) is shown exactly as it is in the snapshot.
- **Statuses** are codes in the data (`'atRisk'`). The label comes from `en.json`
  (`status.atRisk`), the colour from `src/data/statusTone.ts` (`atRisk` → amber).
  `StatusPill` combines both.
- `src/i18n/format.ts` formats dates (always Stockholm time), months, SEK amounts and
  lists ("Peter or Staffan" / "Peter eller Staffan") in the chosen language.

The app is in Swedish and English; Swedish is the default. The switch in the top bar
changes the language, and the choice is saved in the browser (`localStorage`), so it
survives a reload. Dates, months, amounts and lists follow the chosen language.
Every label and separator must be in both `sv.json` and `en.json`: a test fails if the
two files do not have exactly the same keys.

### Interactive state

Only two places keep state that changes while you use the page:

- **Story table** (`src/pages/development/StoryBoard.tsx`): copies the sample stories into
  React state. Add, change status and remove replace that list. The rules are plain
  functions in `stories.ts` (`addStory`, `setStoryStatus`, `removeStory`, `nextStoryId`,
  `storyProgress`), so they are easy to test. Nothing is saved; a reload resets the list.
- **Navigator** (`src/layout/Navigator.tsx`): remembers which question button is selected.

### Folders

| Folder | Contents |
|---|---|
| `src/layout/` | Page frame: layout, sidebar, top bar, Navigator, page list |
| `src/pages/` | One component per page; Development has its own folder |
| `src/components/` | Small shared pieces: `StatusPill`, `SourceTile`, `ItemText`, `UnavailableAction`, icons |
| `src/data/` | Loading (`dataSource`, `projectsClient`, `snapshotClient`, `SnapshotProvider`), last viewed project, contract mapping (`fromContract`), page types, demo sample, `useSnapshot()`, status colours |
| `src/contract/` | The snapshot contract in TypeScript (zod) and `parseSnapshot()` (#10) |
| `src/i18n/` | i18next setup, `sv.json` and `en.json`, saved language, date/number formatting |
| `src/styles/` | Plain CSS: `theme.css` (colours, fonts as variables), `layout.css` (frame), `components.css` (shared classes), `pages.css` (single-page styles) |
| `src/test/` | Test setup and `renderApp()`, which renders the app the same way `main.tsx` does |

### Tests

- `App.test.tsx` clicks through the app like a user: navigation, page titles, Navigator, language switch.
- `StoryBoard.test.tsx` adds, edits and removes stories through the UI.
- `stories.test.ts` tests the story rules directly.
- `i18n.test.ts` checks that `sv.json` and `en.json` have the same keys, the saved language and the formatting helpers.
- `snapshotStates.test.tsx` covers loading, error, fresh, stale (> 24 h), demo and failed-source states.
- Files named `*.contract.test.ts(x)` use the shared fixture in `../contract/`: the contract
  itself, `fromContract`, the client and every page with contract data. They are excluded
  from `tsc` because the Docker build copies only `web/`; Vitest runs them.
- `e2e/smoke.spec.ts` (Playwright, #29) opens the built app in Chromium, at desktop and phone
  size. `e2e/sampleData.ts` answers the data requests with `../contract/fixtures/` and the
  collector's sample START HERE report. The tests fail if a page shows an error, the menu
  breaks or the language switch stops working. CI runs them after the build and keeps the
  traces of failed tests as the `playwright-traces` artifact.

## Common changes

| I want to… | Change |
|---|---|
| Show a new contract field on a page | the page type in `types.ts`, then `fromContract.ts` and its test |
| Change the demo data | `src/data/sampleSnapshot.ts` |
| Change a label or heading | `src/i18n/sv.json` and `src/i18n/en.json` |
| Change colours or fonts | the variables in `src/styles/theme.css` |
| Add a field to the data | `src/data/types.ts` first; the build then lists every place to update |
| Add a status | `StatusCode` in `types.ts`, its colour in `statusTone.ts`, its label in `sv.json` and `en.json` under `status` |
| Add a page | a component in `src/pages/`, a route in `App.tsx`, an entry in `layout/pages.ts`, texts under `pages.<key>` and `nav.<key>` in `sv.json` and `en.json` |

## Where the data comes from

The collector writes one snapshot per project (#99): `out/projects.json` lists the projects
(id and name), and each project has `out/<id>/snapshot.json` and `out/<id>/start-here.md`, in the
shape of the contracts in `../contract/` (#10, #99). The web app serves them at the same paths:
nginx in Docker (`nginx.conf`), Vite in `npm run dev` (`vite.config.ts`).

### Projects and URLs (#100)

- The sidebar lists the projects from `/projects.json`; each opens the page you are on in that
  project, and the selected one is highlighted. Pages are `/<project>/overview`,
  `/<project>/development`, `/<project>/sources` and `/<project>/start-here`, so a URL can be shared.
- `/` and the old routes (`/development` …) go to the last viewed project (`missionControl.project`
  in localStorage), else the first in the list. An unknown id shows "Project not found".
- Switching project loads that project's snapshot without a page reload; the menu stays while
  it loads or fails.

### Loading one project

1. **Fetch** – `src/data/snapshotClient.ts` reads `/<project>/snapshot.json`
   (`src/data/dataSource.tsx` decides where data comes from; tests hand in their own).
2. **Check** – `parseSnapshot()` (`src/contract/snapshot.ts`) validates the file against the
   contract. A wrong file fails here, with the failing fields in the error.
3. **Map** – `fromContract()` turns the domain-shaped contract (work items, pull requests,
   decisions, risks …) into the page-shaped data. Parts the contract does not have yet
   (KPIs, pilots, AWS cost, Navigator texts, source descriptions) become `null` or empty and
   show "No data yet"; nothing is invented.
4. **Provide** – `layout/Layout.tsx` shows a loading or error message, then the pages.
   `useSnapshot()` gives the pages their data.

What the shell shows about the data:

| Situation | What the reader sees |
|---|---|
| Always | "Last updated: …" and "Updated 5 minutes ago" in the top bar |
| Snapshot older than 24 hours | Red banner: run the collector |
| A source has `status: error` | One red banner per source, with its message |
| `npm run dev` and a file missing in `out/` | The fictional demo (two demo projects, sample data) under the "Sample data" banner |
| A file missing or invalid anywhere else | Error message; a deployed app never shows demo data |

To see real data locally, run the collector so it writes `apps/mission-control/out/`
(git-ignored, never commit it), then `npm run dev`.

### START HERE

Each project's START HERE page (`/<project>/start-here`, #26) shows `/<project>/start-here.md`
(#25), with **Download .md** (the file itself) and **Copy** (its exact text, ready to paste into
Claude Code). `src/pages/startHere/markdown.tsx` renders only the Markdown the report uses, as
React elements, so raw HTML in the file is shown as text and only http(s) links are clickable.
Without the file the page says the report has not been generated yet.

## Current limitations

- Many mockup sections show "No data yet" until the contract and pages cover them (#19 onward).
- The snapshot is read once per page load; reload to see a newer one.
- Story edits are lost on reload.
- Search, free-text questions to Navigator, and the "Open …" / "View all" links are shown
  disabled: they have no backend or URLs yet.
- Fonts load from Google Fonts (one external request per page load).
