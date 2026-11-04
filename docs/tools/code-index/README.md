# Rainmaker code index generator (#253)

Builds the **Rainmaker code index**: one searchable HTML page with the classes and files, annotations,
settings and environment variables, endpoints, log messages, database tables and dependencies of G1–G3,
rainmaker.js, the end-to-end test and the test site. Search once and each section shows how many
entries match; links in an entry jump to related entries (a log line to the class that writes it, an
endpoint to its controller, a setting to the class that reads it).

It's a lookup, not a guide. For how the product works end to end, read the team guide "Rainmaker
behind the scenes" in the study-guide hub; the index's "Flow" links point into it.

## Run it

From the repository root, with Node 22 or newer (no `npm install`: only Node's built-in modules):

```
node docs/tools/code-index/index.mjs
```

It reads the working tree, links every entry to the code at the current commit (`git rev-parse HEAD`),
and writes `build/code-index/index.html`, which git ignores. Open that file in a browser. `--out <file>`
writes it elsewhere. It also prints what still needs a note (see below).

Run it on a clean checkout of the commit you want to describe: uncommitted changes are read but the
links point to the commit, so they wouldn't match.

## What is generated, and what is written by hand

| Section | Generated from | Hand-written (in this folder) |
|---|---|---|
| Classes and files | Each class's Javadoc summary, each widget or end-to-end file's JSDoc; the kind from annotations and naming | Which app each module belongs to and its guide chapter (`lib/classes.mjs`) |
| Annotations | Every annotation used in `services/`, `apps/g1`–`g3` and `test-site/`, with counts and examples | What it does and how we use it (`lib/annotations.mjs`, `NOTES`) |
| Settings | `application*.properties`, the test site's `application.yml`, `@DefaultValue`s, the compose files, `.env.example`; the class that reads each property | Nothing: the comments come from the settings files themselves |
| Endpoints | The `@GetMapping` / `@PostMapping` / `@RequestMapping` methods and their Javadoc | Who calls each G1–G3 endpoint and what it answers (`lib/extract.mjs`, `ENDPOINT_NOTES`) |
| Log messages | Every `LOG.…` call: text, level, class, and the code comment above it | What each WARN/ERROR line means (`lib/extract.mjs`, `LOG_NOTES`) |
| Tables | The Flyway migrations: columns, later changes, append-only triggers, the comment above each table, and the classes that use it | Nothing |
| Dependencies | – | Everything (`lib/dependencies.mjs`) |

## When you change the code

The test fails when the code has something new without its note:

```
node --test docs/tools/code-index/test/code-index.test.mjs
```

- **A new annotation:** add an entry to `NOTES` in `lib/annotations.mjs` (or to `SKIP` if it needs no
  explanation, like `@Test`).
- **A new WARN or ERROR log line:** add a pattern and a sentence to `LOG_NOTES` in `lib/extract.mjs`:
  what it means, and what to do.
- **A new endpoint in G1–G3:** add its callers, answers and contract section to `ENDPOINT_NOTES` in
  `lib/extract.mjs`.
- **A new dependency:** add it to `lib/dependencies.mjs`.
- **A new module:** add it to `MODULES` in `lib/classes.mjs` and `APPS` in `lib/extract.mjs`.

The test isn't in CI yet; run it when you touch these, or before refreshing the published page.

## Publishing the page

The team's copy lives in the study-guide hub ("Rainmaker code index"). To refresh it, generate the page on
`develop` and ask Claude to publish `build/code-index/index.html` to the same link (see
`docs/study-guides.md` for how pages are published and listed in the hub). The page is self-contained:
no other files, no network calls except the GitHub links.
