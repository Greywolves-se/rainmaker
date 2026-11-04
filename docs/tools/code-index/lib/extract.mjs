// The "Endpoints", "Log messages", "Tables" and "Settings" sections: read from the request mappings,
// the log calls, the Flyway migrations, the settings files and the compose files.
import {existsSync, readFileSync, readdirSync, statSync} from 'node:fs';
import {join, relative, sep} from 'node:path';

const APPS = {
  'content-ingestion': 'G2', 'content-intent': 'G2', 'matching-ranking': 'G2', 'g2-content-app': 'G2',
  'publisher-control': 'G3', 'advertiser-marketplace': 'G3', 'g3-publisher-app': 'G3',
  'widget-delivery': 'G1', 'tracking-click': 'G1', 'g1-reader-app': 'G1', 'test-site': 'Test site',
};

// ---------- Hand-written: who calls each G1–G3 endpoint and what it answers. A new endpoint needs an
// entry; the test fails until it has one.
const ENDPOINT_NOTES = {
  'GET /widget/rainmaker.js': {callers: 'the publisher\'s page (script tag)', answers: '200 the script (`no-cache`), 404 if it hasn\'t been built', contract: 'D-7'},
  'GET /widget/v1/links': {callers: 'rainmaker.js, once per page view', answers: '200 links (or `[]`), 400 not a publisher article, 503 G3 couldn\'t answer', contract: '7.4'},
  'GET /c/{token}': {callers: 'the reader\'s browser (a click)', answers: '302 to the offer (click stored), 400 invalid link, 410 expired, 503 click couldn\'t be stored', contract: '7.5'},
  'HEAD /c/{token}': {callers: 'link checkers, e-mail and chat previews', answers: 'the same status and `Location` as GET, never stored', contract: '7.5'},
  'GET /api/articles': {callers: 'G3: the poll (`?recent=100`) and the live check (`?url=`)', answers: '200 articles with classification and suggestions, 400 bad parameters', contract: '7.1'},
  'GET /api/approved-links': {callers: 'G1, for each widget request', answers: '200 the approved links of the current green version (or `[]`), 400 bad url, 503 G2 couldn\'t answer', contract: '7.3'},
  'GET /api/offers': {callers: 'G2\'s matching', answers: '200 every offer (id, category, CPC)', contract: '7.2'},
  'GET /landing/{offerId}': {callers: 'the reader, after a click (demo destination)', answers: '200 the landing page (sv/en), 404 unknown offer', contract: '7.2'},
  'GET /publisher/articles': {callers: 'the editor', answers: '200 the editor page (100 most recent articles)', contract: '7.6'},
  'POST /publisher/suggestions/{suggestionId}/approve': {callers: 'the editor page\'s Godkänn button', answers: '303 back to the page, 404 unknown, 409 can\'t be decided (old version, withdrawn, not green)', contract: '7.6'},
  'POST /publisher/suggestions/{suggestionId}/reject': {callers: 'the editor page\'s Avvisa button', answers: '303 back to the page, 404 unknown, 409 can\'t be decided', contract: '7.6'},
};

// ---------- Hand-written: what a WARN or ERROR log line means, matched on its text. A new WARN or
// ERROR line needs a note; the test fails until it has one.
const LOG_NOTES = [
  [/^Could not read the feed/, 'G2 couldn\'t read the publisher\'s RSS feed (site down, slow, or not valid RSS). Nothing is stored; the next poll tries again.'],
  [/^Skipped feed item/, 'One item in the feed lacks a link, title or date. Only that article is skipped until the feed is fixed.'],
  [/^Skipped .*not a valid address|^Skipped .*not on the feed/, 'A feed item points to a bad address or another site. It is never fetched (ingestion is read-only, on the feed\'s own site).'],
  [/^Skipped .*until the next poll/, 'One article page couldn\'t be fetched or has no text or section. It is tried again on every poll.'],
  [/not classified for now; it is treated like red/, 'Classification failed with an unexpected error (often the database). The version is treated like red; the scheduled retry picks it up if a row was stored.'],
  [/^Bedrock call failed/, 'Bedrock kept failing (throttling, timeout, service or network error) after the retries. Stored as `failed`; the retry tries again in 5 minutes.'],
  [/^Bedrock access denied/, 'Access denied: the role lacks permission, or AWS\'s cost stop (SCP) is active. Stored as `failed`. Needs a person.'],
  [/^Bedrock call could not be made/, 'Usually an expired SSO login or a wrong setting. Run `aws sso login` and restart G2.'],
  [/^Bedrock circuit breaker/, 'Bedrock kept failing: no calls for a minute, articles stay `unclassified` and are retried later.'],
  [/has no headline or text; stored as failed/, 'The article had nothing to classify. Stored as `failed` without calling Claude.'],
  [/^Classifier threw/, 'An unexpected error inside the classifier. Stored as `failed` (never green by default).'],
  [/not classified again; it is still treated like red|Classifying versions again failed/, 'The classification retry hit an error. The versions are tried again in the next run.'],
  [/is not in content-ingestion; not classified again/, 'The retry couldn\'t find the article\'s text in content-ingestion.'],
  [/^No suggestions for article .* for now/, 'Matching couldn\'t get G3\'s offers. The green version waits for offers and is matched again once G3 answers (#248).'],
  [/^Skipping offer/, 'An offer from G3 has no id or an unknown category; it is never suggested.'],
  [/G3's offers API is still unavailable/, 'The matching retry ran while G3 was still down; the waiting articles try again next run.'],
  [/^Matching article .* again failed|^Matching articles again failed/, 'The matching retry hit an error for one article (or the run); the rest go on, and it is tried again after the interval.'],
  [/unknown topic .* skipped/, 'A waiting article\'s stored topic is no longer a known topic; that topic is skipped.'],
  [/^Could not read G2's articles/, 'G3\'s poll couldn\'t read G2 (down, slow or bad answer). The editor page keeps showing the last copy; the next poll (5 s) tries again.'],
  [/^No approved links/, 'G3 couldn\'t ask G2 whether the article is green, so it answered 503 (fail closed): the reader gets no next step.'],
  [/^Approved offer .* no longer exists/, 'An approved offer was removed from S4; its link is left out.'],
  [/^No widget links/, 'G1 couldn\'t get the approved links from G3 (down, slow, bad answer or circuit open) and answered 503: no next step for that reader.'],
  [/^G2 articles reader circuit breaker/, 'G3\'s circuit breaker for its live call to G2 changed state. While open (10 s), the approved links API answers 503 at once and G2 isn\'t asked; the poll is not affected.'],
  [/^G3 approved links circuit breaker/, 'G1\'s circuit breaker for G3 changed state. While open (10 s), no reader gets a next step and G3 isn\'t called.'],
  [/^Left out .*without all its fields|^Left out offerId/, 'One link from G3 was incomplete or couldn\'t be signed; only that link is dropped.'],
  [/^Widget links refused/, 'The widget asked for an address that isn\'t an article on a publisher site, or is too long. 400, G3 not asked.'],
  [/^Click not stored, so not redirected/, 'The click couldn\'t be stored (database down or slow): the reader got the "try again" page and was not redirected (fail closed for money).'],
  [/^Click link refused/, 'A forged, changed or expired click link: 400 or 410 page, nothing stored.'],
  [/^rainmaker.js not found/, 'G1 started before the widget was built: run `npm run build` in `widget/`. Pages work without it.'],
  [/RAINMAKER_CLICK_SIGNING_KEY is not set/, 'Local profile only: click links are signed with a random key and stop working when G1 restarts.'],
  [/^Fake classifier active/, 'G2 runs without Claude: every article gets the fake zone (red by default), so no next step shows.'],
  [/^Content change refused/, 'Test site: an article file is invalid, so the whole change is refused and the last good content is still served.'],
  [/^Request failed/, 'Test site: a page failed with an unexpected error; the reader got the site\'s error page.'],
  [/^Article classification/, 'One Bedrock classification: its `outcome` (classified, failed-…, deferred-…) and the tokens it cost.'],
];

/** The endpoint, log, table and setting entries of the checkout at `root`, linked to commit `sha`. */
export function extract(root, sha) {
  const BLOB = `https://github.com/Greywolves-se/rainmaker/blob/${sha}/`;
  const rel = path => relative(root, path).split(sep).join('/');
  const read = path => readFileSync(path, 'utf8');
  function files(dir, test) {
    if (!existsSync(dir)) return [];
    const found = [];
    for (const name of readdirSync(dir)) {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) found.push(...files(path, test));
      else if (test(path)) found.push(path);
    }
    return found;
  }

  const moduleOf = path => (path.startsWith('test-site/') ? 'test-site' : path.split('/')[1]);
  const javaDirs = [
    ...Object.keys(APPS).filter(m => !m.startsWith('g') && m !== 'test-site').map(m => join(root, 'services', m, 'src', 'main', 'java')),
    ...['g1-reader-app', 'g2-content-app', 'g3-publisher-app'].map(m => join(root, 'apps', m, 'src', 'main', 'java')),
    join(root, 'test-site', 'src', 'main', 'java'),
  ];
  const java = javaDirs.flatMap(dir => files(dir, p => p.endsWith('.java'))).map(path => ({path: rel(path), source: read(path)}));
  const className = file => file.path.split('/').pop().replace('.java', '');

  /** Java string literals at the start of an expression, joined: "a" + "b" -> ab. */
  function literals(expression) {
    let text = '';
    for (const match of expression.matchAll(/"((?:[^"\\]|\\.)*)"/g)) text += match[1].replace(/\\"/g, '"').replace(/\\n/g, ' ');
    return text;
  }
  /** Text from an opening parenthesis to its match, skipping strings. */
  function balanced(source, open) {
    let depth = 0;
    for (let i = open; i < source.length; i++) {
      const c = source[i];
      if (c === '"') { i++; while (i < source.length && source[i] !== '"') { if (source[i] === '\\') i++; i++; } continue; }
      if (c === '(') depth++;
      if (c === ')' && --depth === 0) return source.slice(open + 1, i);
    }
    return source.slice(open + 1);
  }
  /** Splits call arguments at top-level commas, minding strings (with escapes) and brackets. */
  function splitArgs(text) {
    const parts = [];
    let depth = 0;
    let start = 0;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (c === '"') { i++; while (i < text.length && text[i] !== '"') { if (text[i] === '\\') i++; i++; } continue; }
      if ('([{'.includes(c)) depth++;
      if (')]}'.includes(c)) depth--;
      if (c === ',' && depth === 0) { parts.push(text.slice(start, i)); start = i + 1; }
    }
    parts.push(text.slice(start));
    return parts.map(p => p.trim());
  }
  const lineOf = (source, index) => source.slice(0, index).split('\n').length;
  function docBefore(source, index) {
    const before = source.slice(0, index);
    const end = before.lastIndexOf('*/');
    const start = before.lastIndexOf('/**');
    if (start < 0 || end < start || /[;{}]/.test(before.slice(end + 2).replace(/@\w+(\([^)]*\))?/g, ''))) return '';
    return before.slice(start + 3, end).split('\n').map(l => l.replace(/^\s*\* ?/, '')).join(' ')
      .split(/<p>|\s@param|\s@return/)[0]
      .replace(/\{@(?:code|link) ([^}]*)\}/g, '`$1`').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
  }

  // ---------- Constants, for paths built from them (PATH_PREFIX + "{token}").
  // A constant is looked up in its own class first: two classes each have a PATH_PREFIX.
  const constants = new Map();
  const localConstants = new Map();
  for (const file of java) {
    const local = new Map();
    for (const m of file.source.matchAll(/static final String (\w+)\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+);/g)) {
      local.set(m[1], literals(m[2]));
      constants.set(`${className(file)}.${m[1]}`, literals(m[2]));
    }
    localConstants.set(file.path, local);
  }
  const resolve = (expression, file) => expression.split('+').map(part => {
    part = part.trim();
    if (part.startsWith('"')) return literals(part);
    return localConstants.get(file.path).get(part) ?? constants.get(part) ?? `{${part}}`;
  }).join('');

  // ---------- Endpoints.
  const endpoints = [];
  for (const file of java) {
    if (!/@(Rest)?Controller\b/.test(file.source)) continue;
    for (const m of file.source.matchAll(/@(GetMapping|PostMapping|RequestMapping)\(/g)) {
      const args = balanced(file.source, m.index + m[0].length - 1);
      const parts = splitArgs(args);
      const named = parts.find(p => /^(path|value)\s*=/.test(p));
      const pathExpr = named ? named.replace(/^(path|value)\s*=\s*/, '') : parts[0];
      const path = resolve(pathExpr, file);
      const method = m[1] === 'GetMapping' ? 'GET' : m[1] === 'PostMapping' ? 'POST' : ((args.match(/RequestMethod\.(\w+)/) || [])[1] || 'ANY');
      const after = file.source.slice(m.index + m[0].length + args.length);
      const handler = (after.match(/\n\s*(?:public\s+|private\s+|protected\s+)?[\w<>?,.\s\[\]]+?\s(\w+)\s*\(/) || [])[1] || '';
      const key = `${method} ${path}`;
      const module = moduleOf(file.path);
      endpoints.push({
        method, path, app: APPS[module], module, controller: className(file), classPath: file.path, handler,
        summary: docBefore(file.source, m.index), url: `${BLOB}${file.path}#L${lineOf(file.source, m.index)}`,
        ...(ENDPOINT_NOTES[key] || {}),
      });
    }
  }

  // ---------- Log messages.
  const logs = [];
  const seen = new Set();
  for (const file of java) {
    for (const m of file.source.matchAll(/LOG\s*\.\s*(trace|debug|info|warn|error|atTrace|atDebug|atInfo|atWarn|atError)\s*\(/g)) {
      let level = m[1].replace(/^at/, '').toUpperCase();
      let messages = [];
      let keys = [];
      if (m[1].startsWith('at')) {
        const rest = file.source.slice(m.index, file.source.indexOf(';', m.index) + 1);
        const logCall = rest.lastIndexOf('.log(');
        const first = logCall >= 0 ? splitArgs(balanced(rest, logCall + 4))[0] : '';
        keys = [...rest.matchAll(/addKeyValue\("(\w+)"/g)].map(k => k[1]);
        if (first.startsWith('"')) messages = [literals(first)];
        else if (/^\w+$/.test(first)) {
          // The message is a variable: take every string assigned to it just before.
          const before = file.source.slice(Math.max(0, m.index - 2500), m.index);
          messages = [...before.matchAll(new RegExp(`\\b${first}\\s*=\\s*"((?:[^"\\\\]|\\\\.)*)"`, 'g'))].map(x => x[1]);
        }
        // (a ? LOG.atError() : LOG.atWarn()): one entry per message, both levels named.
        const ternary = file.source.slice(Math.max(0, m.index - 40), m.index + 40).match(/LOG\.at(\w+)\(\)\s*:\s*LOG\.at(\w+)\(\)/);
        if (ternary) level = `${ternary[1].toUpperCase()} or ${ternary[2].toUpperCase()}`;
      } else {
        messages = [literals(splitArgs(balanced(file.source, m.index + m[0].length - 1))[0])];
      }
      const module = moduleOf(file.path);
      const line = lineOf(file.source, m.index);
      const lines = file.source.split('\n');
      const comment = [];
      for (let i = line - 2; i >= 0 && /^\s*\/\//.test(lines[i]); i--) comment.unshift(lines[i].replace(/^\s*\/\/\s?/, ''));
      for (const message of messages.filter(Boolean)) {
        if (seen.has(file.path + message)) continue;
        seen.add(file.path + message);
        const note = (LOG_NOTES.find(([pattern]) => pattern.test(message)) || [])[1] || '';
        logs.push({message: keys.length ? `${message}  (key-values: ${keys.join(', ')})` : message, level,
          app: APPS[module], module, className: className(file), classPath: file.path,
          note, comment: comment.join(' '), url: `${BLOB}${file.path}#L${line}`});
      }
    }
  }

  // ---------- Tables, from the Flyway migrations.
  const tables = new Map();
  // Only the sources: a build copies every migration into target/classes too.
  const MIGRATIONS = ['src', 'main', 'resources', 'db', 'migration', ''].join(sep);
  for (const path of files(join(root, 'services'), p => p.endsWith('.sql') && p.includes(sep + MIGRATIONS))) {
    const file = rel(path);
    const module = file.split('/')[1];
    const migration = file.split('/').pop();
    const sql = read(path).replace(/\$\$[\s\S]*?\$\$/g, '$$…$$');
    for (const m of sql.matchAll(/CREATE TABLE (\w+)\s*\(/gi)) {
      const body = balanced(sql, m.index + m[0].length - 1);
      const columns = body.split(/,(?![^()]*\))/).map(c => c.trim().replace(/\s+/g, ' ')).filter(c => c && !/^(UNIQUE|PRIMARY KEY|CHECK|FOREIGN KEY|CONSTRAINT)\b/i.test(c));
      const before = sql.slice(0, m.index).split('\n');
      const comment = [];
      for (let i = before.length - 2; i >= 0 && /^\s*--/.test(before[i]); i--) comment.unshift(before[i].replace(/^\s*--\s?/, ''));
      tables.set(`${module}:${m[1]}`, {name: m[1], module, app: APPS[module], database: module.replace(/-/g, '_'), columns,
        created: migration, altered: [], appendOnly: false, comment: comment.join(' '), url: `${BLOB}${file}#L${lineOf(sql, m.index)}`, usedBy: []});
    }
    for (const m of sql.matchAll(/ALTER TABLE (\w+)([^;]*);/gi)) {
      const table = tables.get(`${module}:${m[1]}`);
      if (!table) continue;
      table.altered.push(migration);
      for (const c of m[2].matchAll(/ADD COLUMN (\w+ [^,;]+)/gi)) table.columns.push(c[1].trim().replace(/\s+/g, ' ') + `  (${migration})`);
    }
    for (const m of sql.matchAll(/CREATE TRIGGER \w+\s+BEFORE ([\w\s]+?) ON (\w+)/gi)) {
      if (/UPDATE|DELETE|TRUNCATE/i.test(m[1]) && tables.get(`${module}:${m[2]}`)) tables.get(`${module}:${m[2]}`).appendOnly = true;
    }
  }
  for (const table of tables.values()) {
    const pattern = new RegExp(`\\b(FROM|INTO|UPDATE|JOIN)\\s+${table.name}\\b`, 'i');
    table.usedBy = java.filter(f => moduleOf(f.path) === table.module && pattern.test(f.source)).map(className);
  }

  // ---------- Settings: properties files, the test site's YAML, code defaults, environment variables.
  const settingsClasses = [];
  for (const file of java) {
    for (const m of file.source.matchAll(/@ConfigurationProperties\(\s*(?:prefix\s*=\s*)?"([^"]+)"\s*\)/g)) {
      settingsClasses.push({prefix: m[1], className: className(file), classPath: file.path,
        checked: /throw new Illegal(Argument|State)Exception|\brequire\(/.test(file.source)});
    }
  }
  // A property belongs to the settings class with the longest matching prefix, or to the class that
  // reads it with @Value.
  const ownerOf = key => settingsClasses.filter(s => key.startsWith(s.prefix + '.')).sort((a, b) => b.prefix.length - a.prefix.length)[0];
  const valueOwners = new Map();
  for (const file of java) {
    for (const m of file.source.matchAll(/@Value\("\$\{([\w.-]+)/g)) valueOwners.set(m[1], {className: className(file), classPath: file.path, checked: false});
  }
  const ownerFor = key => ownerOf(key) || valueOwners.get(key);
  const settings = [];
  const envUse = new Map();
  function envRefs(value, key, app, profile) {
    for (let i = value.indexOf('${'); i >= 0; i = value.indexOf('${', i + 2)) {
      let depth = 0;
      let j = i;
      for (; j < value.length; j++) { if (value.startsWith('${', j)) depth++; if (value[j] === '}' && --depth === 0) break; }
      const inner = value.slice(i + 2, j);
      const colon = inner.indexOf(':');
      const name = colon < 0 ? inner : inner.slice(0, colon);
      if (!/^[A-Z][A-Z0-9_]+$/.test(name)) continue;
      const list = envUse.get(name) || [];
      list.push({key, fallback: colon < 0 ? null : inner.slice(colon + 1), app, profile});
      envUse.set(name, list);
    }
  }
  function property(key, value, app, profile, note, url) {
    const owner = ownerFor(key);
    envRefs(value, key, app, profile);
    settings.push({kind: 'property', key, value, app, profile, note,
      owner: owner?.className || null, ownerPath: owner?.classPath || null, checked: Boolean(owner?.checked), url});
  }
  for (const [app, folder] of [['G1', 'g1-reader-app'], ['G2', 'g2-content-app'], ['G3', 'g3-publisher-app']]) {
    const dir = join(root, 'apps', folder, 'src', 'main', 'resources');
    for (const name of readdirSync(dir).filter(n => /^application.*\.properties$/.test(n))) {
      const path = join(dir, name);
      const profile = name === 'application.properties' ? '' : name.replace(/^application-|\.properties$/g, '');
      let comment = [];
      read(path).split('\n').forEach((raw, index) => {
        const line = raw.trim();
        if (!line) { comment = []; return; }
        if (line.startsWith('#')) { comment.push(line.replace(/^#\s?/, '')); return; }
        const eq = line.indexOf('=');
        property(line.slice(0, eq).trim(), line.slice(eq + 1).trim(), app, profile, comment.join(' '), `${BLOB}${rel(path)}#L${index + 1}`);
      });
    }
  }
  // The test site's application.yml: flatten nested keys, keep the comments above each.
  {
    const path = join(root, 'test-site', 'src', 'main', 'resources', 'application.yml');
    const stack = [];
    let comment = [];
    read(path).split('\n').forEach((raw, index) => {
      if (!raw.trim()) { comment = []; return; }
      if (raw.trim().startsWith('#')) { comment.push(raw.trim().replace(/^#\s?/, '')); return; }
      const m = raw.match(/^(\s*)([\w.-]+):\s*(.*)$/);
      if (!m) return;
      const depth = m[1].length / 2;
      stack.length = depth;
      stack[depth] = m[2];
      if (m[3] === '') return;
      property(stack.join('.'), m[3].replace(/^["']|["']$/g, ''), 'Test site', '', comment.join(' '), `${BLOB}${rel(path)}#L${index + 1}`);
      comment = [];
    });
  }
  // Settings with a default in code but no line in a properties file.
  for (const s of settingsClasses) {
    const file = java.find(f => f.path === s.classPath);
    for (const m of file.source.matchAll(/@DefaultValue\("([^"]*)"\)\s+[\w<>]+\s+(\w+)/g)) {
      const key = `${s.prefix}.${m[2].replace(/[A-Z]/g, c => '-' + c.toLowerCase())}`;
      if (settings.some(x => x.key === key)) continue;
      settings.push({kind: 'property', key, value: m[1], app: APPS[moduleOf(s.classPath)], profile: 'default in code', note: '',
        owner: s.className, ownerPath: s.classPath, checked: s.checked, url: `${BLOB}${s.classPath}#L${lineOf(file.source, m.index)}`});
    }
  }
  // Environment variables: where they feed in, the compose files' values and notes, .env.example's note.
  const SERVICES = {g1: 'G1', g2: 'G2', g3: 'G3', 'test-site': 'Test site', postgres: 'PostgreSQL'};
  const composeNotes = new Map();
  for (const composeFile of ['docker-compose.yml', 'docker-compose.bedrock.yml'].filter(f => existsSync(join(root, f)))) {
    const compose = read(join(root, composeFile)).split('\n');
    const serviceAt = index => {
      for (let i = index; i >= 0; i--) { const s = compose[i].match(/^  ([\w-]+):\s*$/); if (s) return s[1]; }
      return '';
    };
    const add = (name, entry) => { const list = composeNotes.get(name) || []; list.push(entry); composeNotes.set(name, list); };
    compose.forEach((raw, index) => {
      const set = raw.match(/^\s+([A-Z][A-Z0-9_]+):\s*(.*)$/);
      if (set) {
        const comment = [];
        for (let i = index - 1; i >= 0 && /^\s*#/.test(compose[i]); i--) comment.unshift(compose[i].trim().replace(/^#\s?/, ''));
        add(set[1], {service: serviceAt(index), value: set[2], note: comment.join(' '), line: index + 1, file: composeFile, sets: true});
      }
      for (const ref of raw.matchAll(/\$\{([A-Z][A-Z0-9_]+)/g)) {
        if (set && set[1] === ref[1]) continue;
        add(ref[1], {service: serviceAt(index), value: raw.trim(), note: '', line: index + 1, file: composeFile, sets: false});
      }
    });
  }
  const envExample = read(join(root, '.env.example')).split('\n');
  const exampleNotes = new Map();
  envExample.forEach((raw, index) => {
    const m = raw.match(/^#?\s*([A-Z][A-Z0-9_]+)=(.*)$/);
    if (!m) return;
    const comment = [];
    for (let i = index - 1; i >= 0 && /^\s*#/.test(envExample[i]) && !/^#?\s*[A-Z][A-Z0-9_]+=/.test(envExample[i]); i--) {
      comment.unshift(envExample[i].replace(/^#\s?/, ''));
    }
    exampleNotes.set(m[1], {value: m[2], note: comment.join(' ')});
  });
  const envNames = new Set([...envUse.keys(), ...composeNotes.keys(), ...exampleNotes.keys()]
    .filter(n => /^(RAINMAKER_|AWS_|BASE_URL|CONTENT_DIRECTORY|PORT$|SPRING_PROFILES_ACTIVE)/.test(n)));
  for (const name of [...envNames].sort()) {
    const feeds = envUse.get(name) || [];
    const inCompose = composeNotes.get(name) || [];
    const example = exampleNotes.get(name);
    const notes = [...new Set([...inCompose.map(c => c.note), example?.note].filter(Boolean))];
    const apps = [...new Set(feeds.map(f => f.app).concat(inCompose.map(c => SERVICES[c.service] || c.service)).filter(Boolean))];
    const defaults = [...new Set(feeds.map(f =>
      (f.fallback === null ? '(none)' : f.fallback === '' ? '(empty)' : f.fallback) + (f.profile ? ` with the ${f.profile} profile` : '')))];
    settings.push({kind: 'environment variable', key: name, value: defaults.join(' · '), app: apps.join(', '), profile: '',
      note: notes.join(' '), feeds: [...new Set(feeds.map(f => f.key))],
      compose: inCompose.map(c => `${c.file === 'docker-compose.yml' ? '' : c.file + ' '}${c.service}: ${c.value}`),
      example: example ? example.value : null, owner: null, ownerPath: null, checked: false,
      url: inCompose[0] ? `${BLOB}${inCompose[0].file}#L${inCompose[0].line}` : null});
  }

  // logFailure in BedrockArticleClassifier picks the level per case: the two that need a person are ERROR.
  for (const log of logs) {
    if (/^Bedrock call failed/.test(log.message)) log.level = "WARN";
    else if (/^Bedrock (access denied|call could not be made)/.test(log.message)) log.level = "ERROR";
  }
  return {endpoints, logs, tables: [...tables.values()], settings};
}
