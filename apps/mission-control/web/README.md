# Mission Control – web

React + TypeScript dashboard for Mission Control, built with Vite (#8). It follows the
Mission Control design mockup: three pages (Overview, Development, Sources) and the
Navigator panel.

## Run

Needs Node.js 20 or newer.

```
npm install
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

The app is in Swedish and English; Swedish is the default. The switch in the top bar
changes the language, and the choice is saved in the browser (`localStorage`), so it
survives a reload. Dates, months, amounts and lists follow the chosen language.

Every UI text, status, label and separator lives in `src/i18n/sv.json` and
`src/i18n/en.json` and is read through react-i18next; components never hard-code text.
A test fails if the two files do not have exactly the same keys, so a new text must be
added to both.

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
