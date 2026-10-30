# Test site (Vintrabladet) - Claude Code instructions

The root `CLAUDE.md` applies here too. These rules add to it for `test-site/`
and take precedence where they are more specific.

## What this module is
A fictional Swedish news site that behaves like a real publisher's site (consent banner,
late ads, tag manager, strict CSP, paywall), used to develop and test the Rainmaker widget,
Content Ingestion (S5) and the AI classification (S6). Not part of the product.
See `README.md`, and `docs/DEVELOPER_GUIDE.md` for a class-by-class walkthrough.

## Rules
- Everything is fictional: invented people, companies, advertisers and the town of Vintra.
  Real places only as travel destinations. Every page keeps the fiction banner and `noindex`.
- The expected brand-safety zone (`expectedZone`) never appears in the page HTML, only in
  `/eval/labels.csv`, so the classifier cannot read the answer. No page links to `/eval/`
  and `robots.txt` disallows it, so a crawler cannot find it either (#160).
- Only the article itself is inside `<article>`. Kicker, byline, ad slots and the paywall
  offer contain no `<p>`, so ingestion's `article p` gets only body text.
- PLUS articles keep their full text in the HTML; CSS hides the locked part.
- No inline scripts or styles and no `innerHTML`: the Content-Security-Policy and the
  tests (`StaticFilesTest`) depend on it.
- Browser features start independently in `static/js/site.js` and may fail without
  breaking the article (fail open).
- No current time and no randomness on the server; pages and feeds are deterministic.
  The only randomness is the ad delay in the browser.
- Content files are validated at start-up, where a broken file stops the site, and again
  when articles or the front page change while it runs, where a broken change is refused
  and the last good version stays (#159). Both name the file and field. Keep that
  behaviour when adding fields or block types.
- All interface text is in `messages.properties`; article and ad text is content and lives
  in `content/`.
- Java package root: `se.rainmaker.testsite`.

## Build and test
From the repository root:
- `./mvnw -pl test-site verify` - format check, Checkstyle, tests, coverage report.
- `./mvnw -pl test-site spring-boot:run` - the site on http://localhost:3000.
- `npm test` in `test-site/` - the Node tests for the consent API and tag manager.
- `docker compose up --build` in `test-site/` - the same site in a container.
