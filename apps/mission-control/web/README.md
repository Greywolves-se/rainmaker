# Mission Control – web

The Mission Control dashboard: React + TypeScript, built with Vite (#8). It follows the
Mission Control design mockup: three pages (Overview, Development, Sources) and the
Navigator panel on the right.

> **All data on the pages is fictional.** It comes from `src/data/sampleSnapshot.ts`,
> copied from the mockup. See [Switching to real data](#switching-to-real-data).

## Contents

- [Run it](#run-it)
- [How the app is put together](#how-the-app-is-put-together)
- [Common changes](#common-changes)
- [Switching to real data](#switching-to-real-data)
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
```

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
sampleSnapshot.ts  →  useSnapshot()  →  Layout and the three pages  →  smaller components
```

- `src/data/types.ts` defines the shape of the data (`Snapshot`). TypeScript checks every
  component against it, so a missing or misspelled field fails the build.
- `src/data/sampleSnapshot.ts` is one object of that shape, filled with fictional data.
- `src/data/useSnapshot.ts` is the **only** way components get data. No component imports
  the sample directly. This is what makes the switch to real data a small change.
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
| `src/data/` | Data types, the sample snapshot, `useSnapshot()`, status colours |
| `src/i18n/` | i18next setup, `sv.json` and `en.json`, saved language, date/number formatting |
| `src/styles/` | Plain CSS: `theme.css` (colours, fonts as variables), `layout.css` (frame), `components.css` (shared classes), `pages.css` (single-page styles) |
| `src/test/` | Test setup and `renderApp()`, which renders the app the same way `main.tsx` does |

### Tests

- `App.test.tsx` clicks through the app like a user: navigation, page titles, Navigator, language switch.
- `StoryBoard.test.tsx` adds, edits and removes stories through the UI.
- `stories.test.ts` tests the story rules directly.
- `i18n.test.ts` checks that `sv.json` and `en.json` have the same keys, the saved language and the formatting helpers.

## Common changes

| I want to… | Change |
|---|---|
| Change what the page shows (today) | `src/data/sampleSnapshot.ts` |
| Change a label or heading | `src/i18n/sv.json` and `src/i18n/en.json` |
| Change colours or fonts | the variables in `src/styles/theme.css` |
| Add a field to the data | `src/data/types.ts` first; the build then lists every place to update |
| Add a status | `StatusCode` in `types.ts`, its colour in `statusTone.ts`, its label in `sv.json` and `en.json` under `status` |
| Add a page | a component in `src/pages/`, a route in `App.tsx`, an entry in `layout/pages.ts`, texts under `pages.<key>` and `nav.<key>` in `sv.json` and `en.json` |

## Switching to real data

The collector (#7) will write one JSON snapshot into `apps/mission-control/out/`, in the
shape defined by the snapshot contract (#10). Neither exists yet. When they do, make the
change below. Pages and components should not need to change, apart from adjusting to
whatever shape #10 defines.

### Step 1 – Match the types to the contract

Update `src/data/types.ts` so `Snapshot` matches the JSON Schema from #10, then run
`npm run build`. TypeScript lists every component that uses a field that changed or
disappeared. Fix those, and update `sampleSnapshot.ts` to the new shape too: the tests keep
using it.

### Step 2 – Make the snapshot file reachable from the browser

During development, the simplest way is to copy the collector's output into `public/`.
Vite serves everything in `public/` from the site root, so `public/snapshot.json` becomes
`http://localhost:5173/snapshot.json`.

The snapshot contains internal project data and **must never be committed**. Add this to
the root `.gitignore` before copying it:

```
apps/mission-control/web/public/snapshot.json
```

(The file name `snapshot.json` is an assumption; use whatever name the collector writes.
Serving it some other way, e.g. a small script or the collector writing straight into
`public/`, is a team decision.)

### Step 3 – Load the file instead of importing the sample

Today `useSnapshot()` returns the sample immediately. Real data arrives later (a network
request), so it needs a loading step. Put the loaded snapshot in a React context, so
`useSnapshot()` keeps the same signature and **no page has to change**.

Create `src/data/loadSnapshot.ts`:

```ts
import type {Snapshot} from './types';

export async function loadSnapshot(url = '/snapshot.json'): Promise<Snapshot> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Snapshot request failed: HTTP ${response.status}`);
  }
  const data: unknown = await response.json();
  if (!isSnapshot(data)) {
    throw new Error('Snapshot does not match the expected shape');
  }
  return data;
}

/** Cheap shape check: catches a wrong or old file before the pages crash on it. */
function isSnapshot(data: unknown): data is Snapshot {
  if (typeof data !== 'object' || data === null) {
    return false;
  }
  const required = [
    'generatedAt',
    'overview',
    'development',
    'sources',
    'navigator',
  ];
  return required.every(key => key in data);
}
```

Replace `src/data/useSnapshot.ts` with a context:

```tsx
import {createContext, useContext, type ReactNode} from 'react';
import type {Snapshot} from './types';

const SnapshotContext = createContext<Snapshot | null>(null);

export function SnapshotProvider({
  snapshot,
  children,
}: {
  snapshot: Snapshot;
  children: ReactNode;
}) {
  return (
    <SnapshotContext.Provider value={snapshot}>
      {children}
    </SnapshotContext.Provider>
  );
}

export function useSnapshot(): Snapshot {
  const snapshot = useContext(SnapshotContext);
  if (!snapshot) {
    throw new Error('useSnapshot() must be used inside <SnapshotProvider>');
  }
  return snapshot;
}
```

(Rename the file to `useSnapshot.tsx` because it now contains JSX.)

Create `src/data/SnapshotLoader.tsx`, which loads once and shows a message while loading
or on failure:

```tsx
import {useEffect, useState, type ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {loadSnapshot} from './loadSnapshot';
import type {Snapshot} from './types';
import {SnapshotProvider} from './useSnapshot';

type LoadState =
  | {status: 'loading'}
  | {status: 'error'}
  | {status: 'ready'; snapshot: Snapshot};

export function SnapshotLoader({children}: {children: ReactNode}) {
  const {t} = useTranslation();
  const [state, setState] = useState<LoadState>({status: 'loading'});

  useEffect(() => {
    let cancelled = false;
    loadSnapshot().then(
      snapshot => {
        if (!cancelled) {
          setState({status: 'ready', snapshot});
        }
      },
      (error: unknown) => {
        console.error('Mission Control: could not load the snapshot', error);
        if (!cancelled) {
          setState({status: 'error'});
        }
      }
    );
    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === 'loading') {
    return <p role="status">{t('snapshot.loading')}</p>;
  }
  if (state.status === 'error') {
    return <p role="alert">{t('snapshot.error')}</p>;
  }
  return (
    <SnapshotProvider snapshot={state.snapshot}>{children}</SnapshotProvider>
  );
}
```

Add the two texts to `en.json` (and the Swedish ones to `sv.json`):

```json
"snapshot": {
  "loading": "Loading the latest snapshot …",
  "error": "Could not load the snapshot. Run the collector and reload the page."
}
```

In `src/main.tsx`, wrap `<App />`:

```tsx
<BrowserRouter future={ROUTER_FUTURE}>
  <SnapshotLoader>
    <App />
  </SnapshotLoader>
</BrowserRouter>
```

### Step 4 – Keep the tests on sample data

Tests must not depend on a real snapshot. In `src/test/renderApp.tsx`, wrap `<App />` in
`<SnapshotProvider snapshot={SAMPLE_SNAPSHOT}>`. Every existing test then keeps working
unchanged.

Add `SnapshotLoader` tests with a mocked `fetch` (`vi.stubGlobal('fetch', ...)`) for the
three cases: loading message, error message (HTTP error and wrong shape), and the app
rendering once the data arrives.

### Step 5 – Check

```
npm run fix      # formats new code to the gts style
npm run build && npm run lint && npm test
```

Then run the collector, copy its output to `public/snapshot.json`, start `npm run dev`,
and check each page against the real data.

### Things to decide when doing this

- **Stories.** The Development table currently edits stories in the browser only. Real
  stories come from GitHub issues; writing them back to GitHub is separate work.
- **Validation.** `isSnapshot()` above only checks the top-level fields. If the contract
  changes often, validating against the #10 JSON Schema is safer, but needs a new
  dependency (e.g. a schema validator). Ask the team before adding one.
- **Refreshing.** The loader reads the snapshot once per page load. Automatic refresh is a
  later feature.

## Current limitations

- All data is fictional (see above).
- Story edits are lost on reload.
- Search, free-text questions to Navigator, and the "Open …" / "View all" links are shown
  disabled: they have no backend or URLs yet.
- Fonts load from Google Fonts (one external request per page load).
