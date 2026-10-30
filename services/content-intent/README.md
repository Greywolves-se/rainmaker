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
    The app's `ModuleBoundaryTest` fails the build on an unnamed one, another module's, or
    `jakarta.transaction.Transactional` (#170).

## Classification (#148)

- **`api`:** the contract's types (`docs/rainmaker/contract.md`, section 3.2): `Classification`,
  `ClassificationStatus`, `Zone`, `Topic`, `Reason`, `ArticleToClassify`, the `ArticleClassifier`
  interface, and the `ArticleClassified` event (section 6.2), which matching listens to (#149).
  `topics` keep Claude's order, most relevant first: matching ranks by it. Only a classified green article may get suggestions (`mayGetSuggestions()`, D-4);
  failed and unclassified are treated like red everywhere.
- **`classification`** (internal):
  - `ClaudeAnswerParser` accepts Claude's answer only if it matches the contract's schema
    (`src/main/resources/content-intent/classification-answer.schema.json`, a copy of section 8;
    change both together), with both reasons non-blank. Anything else means `failed`.
  - `FakeArticleClassifier` gives every article one configured answer, for local work and tests.
  - `BedrockArticleClassifier` calls Claude on Amazon Bedrock (Converse API, AWS SDK v2) with the
    prompt in `src/main/resources/content-intent/classification-prompt.txt` and the schema as a tool
    Claude must call. Only the headline and text are sent, as data.
  - `ClassifierConfiguration` picks one of them from the settings below.
- **`pipeline`** (internal, #209): classifies every stored article (contract 6.1 → 3.2 → 6.2).
  - `ArticleClassification` listens to content-ingestion's `ArticleStored`. It stores the version
    as `unclassified` at once (a version stored already is a repeated event: nothing changes),
    calls the classifier outside any database transaction, then has the result stored. A
    classifier that throws gives `failed`. The listener runs inside ingestion's feed poll, so any
    other error is logged and the poll continues; that version stays `unclassified`, and nothing
    classifies it again yet (#223).
  - `ClassificationResults` stores the result and publishes `ArticleClassified` in one transaction,
    for every status, so matching withdraws suggestions for anything that isn't green.
  - `ClassificationStore`: table `classification`, one row per article id and version
    (`V2__classifications.sql`). Never the text.
  - It also answers `api.CurrentClassifications`: the current classification of a list of
    `ArticleVersion`s in one query, for the articles API (#202). A version with nothing stored is
    `unclassified`.

### Settings (`rainmaker.content-intent.classifier.*`, in G2's `application.properties`)

| Setting | Default | What it does |
|---|---|---|
| `mode` | `fake` (`RAINMAKER_CLASSIFIER_MODE`) | `fake` needs no AWS; `bedrock` calls Claude |
| `region` | `eu-north-1` | Where Bedrock is called |
| `model-id` | `eu.anthropic.claude-haiku-5-5` (`RAINMAKER_CLASSIFIER_MODEL_ID`) | The EU inference profile the G2 role may call (#161) |
| `max-tokens` | `400` | Most tokens per answer; the answer is a short JSON |
| `max-text-chars` | `20000` | Longer article texts are cut before sending, so one call can't use a large share of the daily cap |
| `timeout` | `20s` | One call, including the answer |
| `max-attempts` / `backoff` | `3` / `1s` | Tries for temporary errors only; the wait roughly doubles each time, with jitter |
| `calls-per-minute` | `20` | More calls in any minute leave the article `unclassified` |
| `daily-token-cap` | `200000` | When used, articles stay `unclassified` until the next UTC day |
| `circuit-breaker.*` | `50` %, `10`, `5`, `1m` | Opens when at least `failure-rate-percent` of the last `window-size` articles (at least `minimum-calls` seen) still failed after their retries; then no call for `open-for`, and one trial call decides (#185) |
| `fake.*` | **red** (`RAINMAKER_FAKE_ZONE`), `home` | The fake classifier's answer for every article. Red by default, so nothing gets a panel without Claude; set `RAINMAKER_FAKE_ZONE=green` only for local work on matching or the widget. G2 logs a WARN at start-up while the fake is active |

### What happens when something goes wrong

| Situation | Result | Retried? |
|---|---|---|
| Calls per minute or daily token cap used | `unclassified` (no call made) | Not yet; only an edited article is classified again (#223) |
| Circuit breaker open (Bedrock kept failing) | `unclassified` (no call made); one WARN when it opens and one when it closes | Not yet; only an edited article is classified again (#223) |
| Throttling, timeout, service error, network error (e.g. a reset connection) | `failed` once `max-attempts` are used; counts toward the circuit breaker | Yes, with back-off and jitter |
| Other client error, e.g. an expired SSO login | `failed`, logged as ERROR (`failed-client-error`) | No |
| `AccessDenied` (also the hard cost stop's SCP, #161) | `failed`, logged as ERROR (`failed-access-denied`) | No |
| Validation error, unknown model | `failed` | No |
| Refusal, cut-off answer, text instead of the tool, invalid answer | `failed` | No |

Never green by default. Logs carry the article id, version, outcome and tokens, never the text.

### Running against Bedrock locally

Needs #161 deployed, the permission set `RainmakerBedrockDeveloper`, and Bedrock quota:

```
aws sso login --profile rainmaker-dev
AWS_PROFILE=rainmaker-dev RAINMAKER_CLASSIFIER_MODE=bedrock ./mvnw -pl apps/g2-content-app spring-boot:run
```

Tests never call Bedrock: they use the fake classifier or a stubbed SDK client (CLAUDE.md).
