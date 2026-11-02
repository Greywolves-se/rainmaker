# S5 Content Ingestion (`content-ingestion`)

Reads new and edited articles from the publisher's feed (in the demo, the test site's RSS feed) and stores them.

- **Runs in:** G2 (`apps/g2-content-app`)
- **Built in:** #147 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.

## What it does (#147)

Every `poll-interval` (default 1 minute; the first poll runs at start-up) it reads the feed and,
for each item:

1. **Known URL, same update time:** skipped without reading the page, so nothing is stored twice.
2. **New URL, or a newer `<atom:updated>`:** reads the article page for the body text (every `<p>`
   inside `<article>`) and the section (the `/sektion/<id>` link), stores it as a new row in
   `article_version` (version 1, or one more than before) and publishes
   `api.ArticleStored` (contract section 6.1) as a Spring event for content-intent.
3. **Errors:** a feed that is down or invalid is logged and the poll ends; a page that fails
   skips only that article. The next poll tries again. Pages on another site than the feed's
   are never read.

**Reading the articles back (#202):** `api.LatestArticles` gives the latest version of each article,
the most recently stored first or by URL, without the body text (`ingestion/JdbcLatestArticles`). G2's
articles API (contract 7.1) uses it.

It only sends GET requests: ingestion never writes to the publisher (product rule). The body text
is never logged (`ArticleStored.toString()` leaves it out).

| Setting (`rainmaker.content-ingestion.feed.*`) | Default | |
|---|---|---|
| `url` | `http://localhost:3000/rss.xml` | G2 reads `RAINMAKER_FEED_URL` |
| `poll-interval` | `1m` | between the end of one poll and the next |
| `timeout` | `5s` | connect and read timeout per call |
| `polling` | `true` | `false` turns the schedule off (G2's start-up test) |

**Try it:** start PostgreSQL (`docker compose up -d`), the test site
(`./mvnw -pl test-site spring-boot:run`) and G2 (see its README). The log shows
`Stored version 1 of …` for each article; add or edit a file in `test-site/content/articles/`
and it is stored within one poll.

**Known limits (demo):** the page rules match the test site's markup; a real publisher needs its
own. An `ArticleStored` lost in a crash is not sent again (no outbox yet, contract 6.1).

## Package and database

- **Package:** `se.rainmaker.contentingestion`. Other modules may only use `se.rainmaker.contentingestion.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own PostgreSQL database `content_ingestion` with its own login, never another module's
  (architecture D16). `database/ContentIngestionDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/content-ingestion/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts; `V2__article_version.sql` is #147's table;
  - inject the client with `@Qualifier(ContentIngestionDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(ContentIngestionDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.
    The app's `ModuleBoundaryTest` fails the build on an unnamed one, another module's, or
    `jakarta.transaction.Transactional` (#170).
