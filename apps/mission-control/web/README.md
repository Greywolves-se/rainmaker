# Mission Control – web

React + TypeScript dashboard for Mission Control, built with Vite (#8). It follows the
Mission Control design mockup: three pages (Overview, Development, Sources) and the
Navigator panel.

## Run

Needs Node.js 22 or newer.

```
npm ci
npm run dev      # local dev server
npm run build    # type check + production build into dist/
npm run lint     # ESLint with the gts (Google TypeScript style) rules
npm run fix      # auto-fix lint and formatting
npm test         # Vitest + React Testing Library
```

## Data

All numbers and texts on the pages come from `src/data/sampleSnapshot.ts`. That data is
**fictional**: it is copied from the design mockup and is not real project status.
`useSnapshot()` is the single place that provides data, so loading the collector's
snapshot (contract in #10) only changes that hook.

Story edits on the Development page live in the browser tab only and are lost on reload.
Search, free-text questions to Navigator and the "Open …" links are shown disabled until
they have a backend or real URLs.

## Text and translations

The app is English only for now. Every UI text, status and label still lives in
`src/i18n/en.json` and is read through react-i18next; components never hard-code text.
Adding Swedish later means adding `sv.json` and a language switch, with no component
changes.

Content from the data (titles, names, notes) is shown as it comes.

## Layout

| Folder | Contents |
|---|---|
| `src/layout/` | Page shell: sidebar, top bar, language switch, Navigator |
| `src/pages/` | One component per page; Development has its own folder |
| `src/components/` | Small shared pieces (status pill, source tile, icons) |
| `src/data/` | Data types, the sample snapshot and status colours |
| `src/i18n/` | i18next setup, translation files and date/number formatting |
| `src/styles/` | Design tokens and CSS taken from the mockup |
