# Rainmaker

Contextual performance marketplace for digital news publishers. A script on the
publisher's site shows a small, labelled panel with relevant commercial links when a
reader hovers a headline. AI suggests the links, an editor approves them, and every
click is tracked for settlement.

We start with **Mission Control**, the team's internal status app
([apps/mission-control](apps/mission-control/README.md)). The other parts are added
one at a time.

## Repository layout

```
rainmaker/
├── pom.xml              parent POM, lists all Maven modules
├── docs/                architecture, decisions, START HERE        (empty for now)
├── services/            one Maven module per logical service; S1–S7 so far (empty, #155)
├── apps/                deployable apps
│   ├── g1-reader-app/       S1 + S2, port 8081
│   ├── g2-content-app/      S5 + S6 + S7, port 8082
│   ├── g3-publisher-app/    S3 + S4, port 8083
│   └── mission-control/     internal status app – built first
├── test-site/           fictional news site that plays an outside publisher (port 3000)
├── widget/              rainmaker.js, TypeScript, not Maven         (empty for now)
└── portals/             React apps, not Maven                       (empty for now)
```

The apps assemble the services (#145, #155); G4 (`apps/g4-money-app`) is added later.
Run one with `./mvnw -pl apps/g1-reader-app -am spring-boot:run`.
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
