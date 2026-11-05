# Rainmaker

Contextual performance marketplace for digital news publishers. A script on the
publisher's site shows relevant, labelled commercial links as a next step once the reader
has read the article: beside it on desktop, after it on mobile
([ADR 0005](docs/adr/0005-behaviour-based-reader-flow.md)). AI suggests the links, an editor
approves them, and every click is tracked for settlement.

The team is building the Rainmaker demo: three apps (G1–G3) that take an article from a
fictional news site through classification, matching and editor approval to a panel and a
stored click. The milestone "Rainmaker demo 30 Oct" on GitHub shows where it stands.
**Mission Control**, the team's internal status app
([apps/mission-control](apps/mission-control/README.md)), is on hold since 21 Oct 2026.

**New here?** Start with the onboarding study guide, pinned at the top of the team's
study-guide hub (the link is in [docs/study-guides.md](docs/study-guides.md)). Then read
[CLAUDE.md](CLAUDE.md), the team's rules for people and Claude alike, and the contract
between the apps, [docs/rainmaker/contract.md](docs/rainmaker/contract.md).

## Repository layout

```
rainmaker/
├── pom.xml              parent POM, lists all Maven modules
├── CLAUDE.md            the team's rules (code, tests, Git, product rules), read by Claude too
├── docs/                the contract (rainmaker/), decisions (adr/), study-guide rules
├── services/            one Maven module per logical service; S1–S7 so far
├── apps/                deployable apps
│   ├── g1-reader-app/       S1 + S2, port 8081
│   ├── g2-content-app/      S5 + S6 + S7, port 8082
│   ├── g3-publisher-app/    S3 + S4, port 8083
│   └── mission-control/     internal status app (on hold)
├── widget/              rainmaker.js, the script on the publisher's page; TypeScript, not Maven
├── test-site/           fictional news site that plays an outside publisher (port 3000)
├── docker-compose.yml   local PostgreSQL; its init script is in docker/postgres/
├── infra/               AWS CDK app (Java): every AWS resource, created through the pipeline
└── portals/             React apps for publishers and admins, not Maven (empty for now)
├── widget/              rainmaker.js, TypeScript, not Maven         (reader flow, ADR 0005, #217–#220)
└── portals/             React apps, not Maven                       (empty for now)
```

The apps assemble the services (#145, #155); G4 (`apps/g4-money-app`) is added later.
Run one with `./mvnw -pl apps/g1-reader-app -am package -DskipTests`, then
`java -jar apps/g1-reader-app/target/g1-reader-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local`
(see the app's README; G1 needs the `local` profile on a developer's machine).

**Local database only.** `docker compose up -d postgres` starts PostgreSQL on `localhost:5432` with
one database and one login per module (`docker/postgres/init/`), e.g. to run an app from your IDE;
the apps' tests start the same setup with Testcontainers, so Docker must be running for
`./mvnw verify`.

## Run it

The whole demo flow with **one command** (#146). Needs only Docker, from a fresh clone:

```
docker compose up --build        # stop with Ctrl+C, or add -d and stop with: docker compose down
```

| What | Address |
|---|---|
| The test site, Vintrabladet, with `rainmaker.js` loaded | http://localhost:3000 |
| G1 Reader & clicks (serves `/widget/rainmaker.js`) | http://localhost:8081 |
| G2 Content & AI | http://localhost:8082 |
| G3 Publisher & marketplace: the editor view | http://localhost:8083/publisher/articles |
| PostgreSQL | `localhost:5432` |

- **The first start** builds the images (a few minutes); later starts reuse them. Add `--build`
  again after you change code.
- **Data is kept** between `docker compose down` and the next `up`. To start from empty databases:
  `docker compose down -v`.
- **New articles:** add or edit a file in `test-site/content/articles/` while it runs; G2 reads the
  feed every minute.
- **End-to-end test (#154):** the demo's "done means" points with Playwright, against this stack
  started with the test's settings. How to run it: [`rainmaker-e2e/README.md`](rainmaker-e2e/README.md).
- **Claude:** G2 uses the fake classifier by default, so no AWS access is needed. It gives every
  article the same zone, **red** by default, so **no panel shows** without Claude: safe, never a
  panel on a red article. For local work on matching or the widget, set `RAINMAKER_FAKE_ZONE=green`
  in `.env`; then every article gets a panel, the red test articles too, so never for the demo.
  The end-to-end test (#154) instead sets `RAINMAKER_FAKE_GREEN_MARKER`: only headlines containing
  it are green, every other article stays red.
  For real Claude, see "Claude on Bedrock" below.
- **How it fits together:** every port is on `127.0.0.1` only. G2 shares the test site's network, so
  it reads `http://localhost:3000/...`, the same article addresses the browser and the widget use
  (contract D-2); other containers reach G2 at `test-site:8082`.

### Claude on Bedrock

With your own AWS login, G2 classifies the articles with Claude Haiku 4.5 in the rainmaker-dev
account (#148, #161). Access is given in IAM Identity Center (the group Rainmaker-Dev), never with
access keys. Everyone uses the same profile names, so the commands in the docs work as written.

1. **Once:** copy the profiles in [`docs/aws-config-example`](docs/aws-config-example) into
   `~/.aws/config`, and copy `.env.example` to `.env` with `AWS_PROFILE=rainmaker-dev-admin`.
2. **Each day:** `aws sso login --profile rainmaker-dev-admin` (opens the browser; the login lasts
   a few hours).
3. **Start:** `docker compose -f docker-compose.yml -f docker-compose.bedrock.yml up --build -d`.
   Your SSO login is mounted read-only; no keys go into any file.
4. **Check:** after a minute, http://localhost:8082/api/articles?recent=100 shows each article's
   zone. The robbery attempt and the traffic accident must be red. An article that shows `failed`
   got no answer from Claude: check `docker compose logs g2`.
5. **Demo:** approve links in the editor view, then open a green article on the test site and keep
   reading for about a minute; the panel appears.

- **Costs:** about half a cent per article. G2 is capped at 20 calls a minute and 200,000 tokens a
  day, and the dev account has a 100 USD monthly budget with a hard stop (infra README).
- **Data is kept:** after `docker compose down` and `up`, the classifications and the approved
  links are still there, and no new calls are made for them. `down -v` starts over.
- **When the login expires:** run `aws sso login` again and `docker compose restart g2`.
  Articles that failed meanwhile are retried every 5 minutes.
- On Windows, if `~/.aws` isn't found, also set `AWS_CONFIG_DIR=C:/Users/<you>/.aws` in `.env`.

**CI.** Each part has its own workflow in `.github/workflows/`: `rainmaker-ci.yml` builds and tests
`services/`, the apps G1–G3 and `widget/` (#156), `mission-control-ci.yml` Mission Control and `infra/`,
`test-site-ci.yml` the test site. The Mission Control and Rainmaker workflows run on every pull request
and skip the jobs for parts it didn't change; the test site's workflow starts only when the test site
changes.

Empty folders contain only a `.gitkeep`, because Git cannot store empty folders.

## Build

Needs JDK 21 or newer (the code targets Java 21). Maven itself is downloaded by the wrapper.

```
./mvnw verify        # macOS / Linux / Git Bash
.\mvnw.cmd verify    # Windows cmd / PowerShell
```

## Code quality

The parent `pom.xml` runs these checks in every Java module during `./mvnw verify`.
Modules get them automatically and never set plugin versions or configuration themselves.

| Check | Tool | When it fails | How to fix |
|---|---|---|---|
| Formatting | Spotless + google-java-format | Code is not formatted in Google style | `./mvnw spotless:apply` |
| Style | Checkstyle (`google_checks.xml`) | Any warning, e.g. naming, missing Javadoc on public members, import order | Fix by hand; the build log shows file and line |
| Coverage | JaCoCo | Only when the module switches the gate on (below) | Add tests |

`./mvnw test` does not run the formatting or style checks, so they do not get in the
way while you write tests; `./mvnw verify` (and CI) does.

**Coverage.** Every module gets a report in `target/site/jacoco/index.html`. CLAUDE.md
asks for at least 80 % line coverage on domain logic. A module switches the gate on in its
own `pom.xml` properties:

```xml
<properties>
  <jacoco.check.skip>false</jacoco.check.skip>
  <!-- Each matching package needs 80 % line coverage. Default: every package. -->
  <jacoco.check.packages>se.rainmaker.clearinghouse.domain*</jacoco.check.packages>
</properties>
```

JaCoCo hooks into the tests through Surefire's `argLine`. If a module sets its own
`argLine`, start it with `@{argLine}`, or coverage is silently lost.

**Architecture tests.** ArchUnit's version is managed in the parent POM. A module adds
the test dependency without a version, and writes its boundary rules as tests:

```xml
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
</dependency>
```

```java
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "se.rainmaker")
class ModuleBoundaryTest {

  /** Other modules may only use the clearing house's public api package (CLAUDE.md). */
  @ArchTest
  static final ArchRule onlyApiPackageIsUsedFromOutside =
      noClasses()
          .that()
          .resideOutsideOfPackage("se.rainmaker.clearinghouse..")
          .should()
          .dependOnClassesThat(
              resideInAPackage("se.rainmaker.clearinghouse..")
                  .and(not(resideInAPackage("se.rainmaker.clearinghouse.api.."))));
}
```

The rule only sees classes on the test classpath, so it belongs in the module (or app)
that depends on other modules.

## Working together

- Git Flow: `feature/<issue-number>-short-name` from `develop`, pull request back to
  `develop`. `main` is production.
- Every PR needs one approval from another developer and must stay under ~400 changed lines.
