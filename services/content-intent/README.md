# S6 Content & Intent (`content-intent`)

Classifies each article with Claude on Amazon Bedrock: topics, brand-safety zone and a short reason.

- **Runs in:** G2 (`apps/g2-content-app`)
- **Built in:** #148 (milestone "Rainmaker demo 30 Oct"); this module was created empty in #155.
- **Package:** `se.rainmaker.contentintent`. Other modules may only use `se.rainmaker.contentintent.api`
  (CLAUDE.md, "Architecture rules"); the app's ArchUnit test enforces it.
- **Data:** its own PostgreSQL database `content_intent` with its own login, never another module's
  (architecture D16). `database/ContentIntentDatabaseConfiguration` sets it up:
  - migrations go in `src/main/resources/db/migration/content-intent/` (`V2__…sql` and up; never edit a
    merged one) and run when the app starts;
  - inject the client with `@Qualifier(ContentIntentDatabaseConfiguration.JDBC_CLIENT) JdbcClient`;
  - mark transactions with `@Transactional(ContentIntentDatabaseConfiguration.TRANSACTION_MANAGER)`; an
    unnamed `@Transactional` is ambiguous, because an app holds several modules' databases.

## Classification (#148)

- **`api`:** the contract's types (`docs/rainmaker/contract.md`, section 3.2): `Classification`,
  `ClassificationStatus`, `Zone`, `Topic`, `Reason`, `ArticleToClassify`, and the `ArticleClassifier`
  interface. Only a classified green article may get suggestions (`mayGetSuggestions()`, D-4);
  failed and unclassified are treated like red everywhere.
- **`classification`** (internal):
  - `ClaudeAnswerParser` accepts Claude's answer only if it matches the contract's schema
    (`src/main/resources/content-intent/classification-answer.schema.json`, a copy of section 8;
    change both together), with both reasons non-blank. Anything else means `failed`.
  - `FakeArticleClassifier` gives every article one configured answer, for local work and tests.
  - The Bedrock implementation, with its rate limit, daily token cap, timeout and retries, follows
    in the next #148 PR.
