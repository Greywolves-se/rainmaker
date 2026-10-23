# S1 Widget Delivery (`widget-delivery`)

Gives rainmaker.js the approved links for an article.

- **Runs in:** G1 (`apps/g1-reader-app`)
- **Built in:** #151 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.widgetdelivery`. Other modules may only use `se.rainmaker.widgetdelivery.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Serves rainmaker.js** at `/widget/rainmaker.js` (#152, contract D-7): `script/WidgetScriptController`
  reads the built file from `rainmaker.widget-delivery.script-location`, with `Cache-Control: no-cache`,
  and answers 404 until `npm run build` in `widget/` has made it. Only for the demo: later S1 delivers
  the script through a CDN (architecture S1), and this goes away.
- **Data:** no database of its own for the demo: it asks G3 for the approved links on each
  request (contract, section 7.3). A Redis projection comes later (architecture S1).
