# Rainmaker end-to-end test (#154)

One Playwright test file that checks the demo's "done means" points (handledare's mail, 20 Oct
2026) on the local stack: the test site, G1, G2 with the fake classifier, G3 and PostgreSQL.

| "Done means" | Where |
|---|---|
| A new article published on the fake-news site shows up in the editor view | `done-means.spec.ts`, the green article's first step |
| After approval, the article shows the panel with the approved links | its second step |
| A click lands on the offer's page and is stored once, also when clicked twice | its third step |
| A red article never shows a panel, even when offers would match | the red article's test |
| With Rainmaker stopped, the article page still loads normally | the last test |
| The whole stack starts with one command, and the test runs from this README | the steps below |

## Run it

You need Docker, and Node.js 22 or newer.

1. **Start the stack with the test's settings**, from the repository root:

   ```sh
   docker compose --env-file rainmaker-e2e/e2e.env up --build -d
   ```

   [`e2e.env`](e2e.env) is used instead of your `.env`:
   - the fake classifier stays red, except headlines with the green marker `[e2e-green]`;
   - G2 reads the feed every 5 s instead of every minute;
   - the widget's qualified reading is 2 s.

   It is only for the test: start the stack without it for the demo.

2. **Run the test**, from this folder:

   ```sh
   npm ci
   npx playwright install chromium
   npm test
   ```

   On a warm stack it takes about half a minute. Right after the stack has started, give it a
   minute: each new article goes through G2 and G3 before the editor view shows it, and the test
   waits up to 2 minutes for that. If the stack wasn't started with `e2e.env`, the test stops at once
   and says so.

3. **Afterwards:** `docker compose down` from the root, then `docker compose up --build -d` without
   `--env-file` for the demo. `docker compose down -v` also empties the databases.

## What it does to the stack

- **It publishes its own articles** as new files in `test-site/content/articles/` (`e2e-*.json`),
  the way #159 publishes, and removes them when it is done. A run that crashes may leave one;
  `.gitignore` keeps them out of commits. G2 and G3 keep their copies of earlier runs' articles,
  which is why the test finds each article by its new address.
- **It approves a suggestion** in the editor view, as `demo-editor`, and clicks the link: the audit
  log and the click table get real rows.
- **It stops G1, G2 and G3** for the last test, and starts them again, waiting until they answer.
- **It reads the click count** with `psql` inside the Postgres container
  (`docker compose exec postgres`), so it needs no database driver.

## Good to know

- **The reader's step is in one place:** `showNextStep` in `tests/support/reader.ts`. Today it
  hovers the headline. When #220 (PR #234) replaces the hover, only that function changes: scroll
  a little, wait the qualified reading (2 s here), and scroll `[data-rainmaker-article-end]` into
  view.
- **Swedish:** the browser asks for Swedish (`sv-SE`), like the demo's editor, and the test looks
  for the editor view's Swedish texts.
- **Not in CI yet** (#154, "Not now"): it needs the whole stack running.
