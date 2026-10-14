# rainmaker.js widget

The script publishers add to their site. When a reader hovers a headline, it shows a small, labelled panel with approved commercial links (served by S1, clicks go through S2).

Not a Maven module: TypeScript, no framework, bundled with esbuild/Vite.

## Requirements (architecture v0.5, sections 4 and 5.5)
- ≤ 15 KB gzipped, loads async, Shadow DOM so it never affects the publisher's page.
- Opens only after a deliberate hover (≥ 300 ms); labelled as advertising ("Annons / Sponsrade länkar").
- No layout shift; keyboard and screen-reader accessible (WCAG 2.1 AA).
- Respects the reader's consent (TCF); fail open: if Rainmaker is down, the page works normally.

Test it against the fictional test publisher site (Valdmora Dagblad) with `RAINMAKER_SCRIPT_URL`.
