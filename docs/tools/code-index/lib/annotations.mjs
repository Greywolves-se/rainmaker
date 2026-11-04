// The "Annotations" section: where each annotation is used in the Rainmaker Java code, joined with a
// hand-written explanation of how we use it.
import {readFileSync, readdirSync, statSync} from 'node:fs';
import {join, relative, sep} from 'node:path';

// ---------- Hand-written: one entry per annotation (or small group). A new annotation in the code
// needs an entry here (or in SKIP); the test fails until it has one.
/** name (or several, sharing one entry) -> group, what it does, how we use it / what to watch. */
const NOTES = [
  {names: ['SpringBootApplication'], group: 'Spring: wiring',
    what: 'Marks the app\'s main class: turns on auto-configuration and component scanning.',
    how: 'Each app lists exactly the service packages it assembles in `scanBasePackages` (e.g. G1 scans `widgetdelivery` and `trackingclick`), so the app states what it runs.'},
  {names: ['Configuration'], group: 'Spring: wiring',
    what: 'A class whose `@Bean` methods create objects for the application context.',
    how: 'In G1–G3 always `proxyBeanMethods = false`: our bean methods never call each other, so Spring needn\'t proxy the class (only the test site\'s `ContentConfiguration` keeps the default). Each module wires itself in its own configuration (`…DatabaseConfiguration`, `PipelineConfiguration`, `MatchingConfiguration`).'},
  {names: ['Bean'], group: 'Spring: wiring',
    what: 'A method in a `@Configuration` class whose return value becomes a bean.',
    how: 'Database beans get module-specific names (`@Bean(JDBC_CLIENT)`, e.g. `trackingClickJdbcClient`), because an app holds several modules\' databases. A schedule is a `SchedulingConfigurer` bean, not `@Scheduled`, so it can be switched off.'},
  {names: ['Component', 'Service', 'Repository'], group: 'Spring: wiring',
    what: 'Marks a class for component scanning, so Spring creates it and injects it where needed.',
    how: 'We mostly use plain `@Component` (listeners, stores, counters). `@Service` and `@Repository` appear once each (`WidgetLinksService`, `ClickStore`) and behave the same for us; there\'s no rule about which to use.'},
  {names: ['Qualifier'], group: 'Spring: wiring',
    what: 'Picks one bean when several of the same type exist.',
    how: 'Every module\'s `JdbcClient` is injected with `@Qualifier(<Module>DatabaseConfiguration.JDBC_CLIENT)`. Without it, Spring can\'t tell the modules\' databases apart and the app doesn\'t start.'},
  {names: ['DependsOn'], group: 'Spring: wiring',
    what: 'Makes a bean wait for another one to be created first.',
    how: 'Each module\'s `JdbcClient` depends on its Flyway bean, so the migrations have run before any code uses the database.'},
  {names: ['Autowired'], group: 'Spring: wiring',
    what: 'Asks Spring to inject a bean into a field, constructor or method.',
    how: '**Only in tests**, on fields of a `@SpringBootTest` class (JUnit creates that class, not Spring). Production code uses constructor injection with no annotation at all; CLAUDE.md forbids `@Autowired` on fields.'},
  {names: ['Value'], group: 'Spring: settings',
    what: 'Injects one property value by its key.',
    how: 'Used once in production (`JdbcOfferCatalog`, the landing base URL). Everything else uses `@ConfigurationProperties` records, which check their values at start-up; prefer those for new settings.'},
  {names: ['ConfigurationProperties'], group: 'Spring: settings',
    what: 'Binds a group of properties (e.g. `rainmaker.tracking-click.link.*`) to a record or class.',
    how: 'Our settings are records with a compact constructor that refuses bad values, so a wrong setting stops the app at start-up instead of misbehaving later (e.g. `MatchingRetrySettings`, `ApprovedLinksProperties`). On a `@Bean` method it binds straight onto that bean (the HikariCP pools).'},
  {names: ['DefaultValue'], group: 'Spring: settings',
    what: 'The value a `@ConfigurationProperties` record component gets when the property isn\'t set.',
    how: 'Used for settings that have a sensible default (`@DefaultValue("5m") Duration interval`). Settings that must be chosen on purpose (timeouts, G1\'s click base URL) have none, so a missing one fails at start-up.'},
  {names: ['EnableConfigurationProperties', 'ConfigurationPropertiesScan'], group: 'Spring: settings',
    what: 'Registers `@ConfigurationProperties` classes so they are bound and injectable.',
    how: 'Each module lists its own settings classes on its configuration (`@EnableConfigurationProperties(OffersProperties.class, …)`). Only the test site scans for them instead.'},
  {names: ['ConditionalOnProperty', 'ConditionalOnBooleanProperty'], group: 'Spring: settings',
    what: 'Creates a bean only when a property has a certain value.',
    how: '`@ConditionalOnBooleanProperty(…, matchIfMissing = true)` on every schedule (the feed poll, G3\'s poll, both retries), so tests can switch a schedule off with `…enabled=false` / `…polling=false`. `@ConditionalOnProperty(mode = bedrock)` creates the Bedrock client only when it\'s used.'},
  {names: ['EnableScheduling'], group: 'Spring: events and scheduling',
    what: 'Turns on Spring\'s task scheduler.',
    how: 'We register schedules through `SchedulingConfigurer` beans with fixed delays (the next run starts after the last one ends). Spring Boot\'s scheduler has one thread by default, shared by every schedule in an app.'},
  {names: ['TransactionalEventListener'], group: 'Spring: events and scheduling',
    what: 'Handles a Spring event after the publisher\'s transaction commits.',
    how: 'Used with `fallbackExecution = true` (content-intent, matching). **Watch out:** when the publisher has no transaction, as in content-ingestion, the listener runs at once, synchronously, on the publisher\'s thread. That\'s why classification runs inside G2\'s feed poll.'},
  {names: ['Transactional'], group: 'Spring: data',
    what: 'Runs a method in one database transaction: everything or nothing.',
    how: 'Always with the module\'s own transaction manager, `@Transactional(<Module>DatabaseConfiguration.TRANSACTION_MANAGER)`. An unnamed one, another module\'s, or `jakarta.transaction.Transactional` fails `ModuleBoundaryTest` (#170).'},
  {names: ['RestController', 'Controller'], group: 'Spring: web',
    what: 'A class that answers HTTP requests. `@RestController` writes the return value as the body (JSON or text); `@Controller` renders a view.',
    how: 'Every API and page in G1–G3 is a `@RestController`, building its HTML itself where it has a page. Only the test site uses `@Controller` with Thymeleaf templates.'},
  {names: ['GetMapping', 'PostMapping', 'RequestMapping'], group: 'Spring: web',
    what: 'Maps an HTTP method and path to a handler method.',
    how: 'Spring answers `HEAD` with the `GET` handler unless a handler is mapped to `HEAD` explicitly. The click redirect has `@RequestMapping(method = HEAD)` for exactly that reason, so a link checker never stores a click (#192). `@PostMapping` is only the editor\'s approve and reject.'},
  {names: ['RequestParam', 'PathVariable', 'RequestHeader'], group: 'Spring: web',
    what: 'Bind a query parameter, a part of the path, or a header to a method parameter.',
    how: 'The widget API takes `url` with `required = false` and validates it itself, so it can answer its own 400 without asking G3. Headers we read: `X-Correlation-Id` (between apps) and `Accept-Language` (Swedish unless English is asked for).'},
  {names: ['ExceptionHandler'], group: 'Spring: web',
    what: 'Turns an exception thrown by a handler into a response.',
    how: 'E.g. G3\'s approved links API answers 503 when G2 can\'t answer. **Watch out:** it runs after the handler\'s try-with-resources has closed, so an MDC value set there (the correlation id) is gone in the handler\'s log line (#252).'},
  {names: ['ControllerAdvice', 'ModelAttribute', 'Order'], group: 'Spring: web',
    what: '`@ControllerAdvice` applies handlers or model attributes to many controllers; `@ModelAttribute` adds a value to every view; `@Order` sorts filters.',
    how: 'Test site only: the error pages, the shared layout values, and the request log filter running first.'},
  {names: ['JsonValue', 'JsonInclude'], group: 'Jackson',
    what: '`@JsonValue` makes one method the JSON form of an object; `@JsonInclude` leaves out empty values.',
    how: 'The annotations keep their old package (`com.fasterxml.jackson.annotation`) even with Jackson 3, whose classes live in `tools.jackson`.'},
  {names: ['SpringBootTest'], group: 'Tests: Spring',
    what: 'Starts the whole application context in a test.',
    how: 'Used for the app tests in G1–G3 (real PostgreSQL, fake other apps) and the test site. `properties = {…}` switches schedules off or shortens settings for that test class. Each distinct set of properties starts a new context.'},
  {names: ['AutoConfigureMockMvc'], group: 'Tests: Spring',
    what: 'Adds a `MockMvc` to a `@SpringBootTest`, to call controllers through the whole filter chain without a real server.',
    how: 'Servlet filters are included, so CORS headers show in these tests (G1\'s widget API tests check them). No real port is opened, so anything that needs one (such as a separate management port) can\'t be tested this way.'},
  {names: ['DynamicPropertySource'], group: 'Tests: Spring',
    what: 'Sets properties computed when the test starts.',
    how: 'Points the app at the Testcontainers database (its port is random) and at WireMock\'s address, via each app\'s `TestDatabases.register`.'},
  {names: ['MockitoBean', 'MockitoSpyBean'], group: 'Tests: Spring',
    what: 'Replaces a bean in the test\'s context with a Mockito mock (or wraps it in a spy).',
    how: 'Spring Boot 4: `@MockBean` and `@SpyBean` are gone. Used for the classifier (`ArticleClassifier`) and the offer catalog in tests, and a spy on content-intent\'s `JdbcClient` to count queries.'},
  {names: ['ActiveProfiles'], group: 'Tests: Spring',
    what: 'Runs a test with a Spring profile.',
    how: 'G1\'s app test runs with `local`, the profile that allows a random click signing key and sets the click base URL.'},
  {names: ['RecordApplicationEvents', 'Import'], group: 'Tests: Spring',
    what: '`@RecordApplicationEvents` records every Spring event in a test; `@Import` adds a configuration class to it.',
    how: 'The classification retry test checks that `ArticleClassified` was published; the test site imports a controller that throws on purpose.'},
  {names: ['Testcontainers', 'Container'], group: 'Tests: containers',
    what: 'Starts a Docker container (here PostgreSQL 18) for a test class and stops it afterwards.',
    how: 'Docker must be running for `./mvnw verify`. The app tests share one database per run, so each test uses its own random ids.'},
  {names: ['RegisterExtension', 'ExtendWith'], group: 'Tests: JUnit',
    what: 'Adds a JUnit 5 extension: `@RegisterExtension` on a field (configurable), `@ExtendWith` on the class.',
    how: 'WireMock (a fake G2 or G3) is a `@RegisterExtension static WireMockExtension`; `@ExtendWith(OutputCaptureExtension.class)` captures log output to check log lines (and that they contain no IP).'},
  {names: ['ParameterizedTest', 'ValueSource', 'MethodSource', 'CsvSource', 'EnumSource', 'NullAndEmptySource'], group: 'Tests: JUnit',
    what: 'One test method run once per input, from values, a method, CSV rows, an enum or null/empty.',
    how: 'Used for "refuses every bad input" tests (bad URLs, bad settings, bad tokens) and "every zone that isn\'t green".'},
  {names: ['Nested', 'NestedTestConfiguration'], group: 'Tests: JUnit',
    what: 'Groups tests in inner classes; `@NestedTestConfiguration(OVERRIDE)` lets an inner class have its own Spring settings.',
    how: 'The test site\'s `RainmakerInstallTest` runs the same checks for each way of installing the widget.'},
];
const SKIP = new Set(['Test', 'Override', 'BeforeEach', 'BeforeAll', 'AfterAll', 'AfterEach', 'FunctionalInterface', 'TempDir', 'SuppressWarnings', 'Deprecated', 'SafeVarargs']);

/**
 * The annotation entries, and the annotations used in the code that have neither an entry nor a
 * place in SKIP (`unexplained`).
 */
export function annotations(root, sha) {
  const BLOB = `https://github.com/Greywolves-se/rainmaker/blob/${sha}/`;

  function files(dir) {
    const found = [];
    for (const name of readdirSync(dir)) {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) found.push(...files(path));
      else if (path.endsWith('.java')) found.push(path);
    }
    return found;
  }
  const dirs = [
    ...readdirSync(join(root, 'services')).filter(m => statSync(join(root, 'services', m)).isDirectory()).map(m => join(root, 'services', m, 'src')),
    ...['g1-reader-app', 'g2-content-app', 'g3-publisher-app'].map(m => join(root, 'apps', m, 'src')),
    join(root, 'test-site', 'src'),
  ];
  const uses = new Map();
  for (const file of dirs.flatMap(dir => { try { return files(dir); } catch { return []; } })) {
    const rel = relative(root, file).split(sep).join('/');
    readFileSync(file, 'utf8').split('\n').forEach((line, index) => {
      const trimmed = line.trim();
      if (trimmed.startsWith('*') || trimmed.startsWith('//') || trimmed.startsWith('/*')) return;
      for (const match of line.matchAll(/(?:^|[\s(,])@([A-Z]\w*)\b/g)) {
        const list = uses.get(match[1]) || [];
        list.push({rel, line: index + 1, test: rel.includes('/test/')});
        uses.set(match[1], list);
      }
    });
  }
  const known = new Set(NOTES.flatMap(note => note.names));
  const unexplained = [...uses.keys()].filter(name => !known.has(name) && !SKIP.has(name));
  const entries = NOTES.map(note => {
    const all = note.names.flatMap(name => (uses.get(name) || []).map(use => ({...use, name})));
    const byFile = new Map();
    for (const use of all) if (!byFile.has(use.rel)) byFile.set(use.rel, use);
    const examples = [...byFile.values()].sort((a, b) => Number(a.test) - Number(b.test)).slice(0, 3)
      .map(use => ({label: `${use.rel.split('/').pop().replace('.java', '')}${note.names.length > 1 ? ' (@' + use.name + ')' : ''}`, url: `${BLOB}${use.rel}#L${use.line}`}));
    return {
      name: note.names.map(name => '@' + name).join(', '), group: note.group, what: note.what, how: note.how,
      uses: all.length, files: byFile.size, main: all.some(use => !use.test), test: all.some(use => use.test), examples,
    };
  }).filter(entry => entry.uses > 0);
  return {entries, unexplained};
}
