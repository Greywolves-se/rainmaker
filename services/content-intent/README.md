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
  - `BedrockArticleClassifier` calls Claude on Amazon Bedrock (Converse API, AWS SDK v2) with the
    prompt in `src/main/resources/content-intent/classification-prompt.txt` and the schema as a tool
    Claude must call. Only the headline and text are sent, as data.
  - `ClassifierConfiguration` picks one of them from the settings below.

### Settings (`rainmaker.content-intent.classifier.*`, in G2's `application.properties`)

| Setting | Default | What it does |
|---|---|---|
| `mode` | `fake` (`RAINMAKER_CLASSIFIER_MODE`) | `fake` needs no AWS; `bedrock` calls Claude |
| `region` | `eu-north-1` | Where Bedrock is called |
| `model-id` | `eu.anthropic.claude-haiku-5-5` (`RAINMAKER_CLASSIFIER_MODEL_ID`) | The EU inference profile the G2 role may call (#161) |
| `max-tokens` | `400` | Most tokens per answer; the answer is a short JSON |
| `timeout` | `20s` | One call, including the answer |
| `max-attempts` / `backoff` | `3` / `1s` | Tries for temporary errors only; the wait doubles each time |
| `calls-per-minute` | `20` | More calls in any minute leave the article `unclassified` |
| `daily-token-cap` | `200000` | When used, articles stay `unclassified` until the next UTC day |
| `fake.*` | green, `home` | The fake classifier's answer |

### What happens when something goes wrong

| Situation | Result | Retried? |
|---|---|---|
| Calls per minute or daily token cap used | `unclassified` (no call made) | Later, by the next run |
| Throttling, timeout, service error | `failed` once `max-attempts` are used | Yes, with back-off |
| `AccessDenied` (also the $100 cost stop's SCP, #161) | `failed` | No |
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
