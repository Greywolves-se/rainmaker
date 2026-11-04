// Writes the code index as one self-contained HTML page: the sections as JSON, with search across
// every section, filters, and links between sections. Colours follow the study guides (light and dark).
import {dependencies} from './dependencies.mjs';

const APP_ORDER = ['G1', 'G2', 'G3', 'Widget', 'E2E', 'Test site'];

/** The study guides' colour tokens, light and dark. */
const TOKENS = `
  :root {
    --bg: #fbfaf7;
    --surface: #ffffff;
    --surface-2: #f2f0ea;
    --text: #1d1d1b;
    --muted: #5d5b55;
    --border: #dedbd2;
    --accent: #1f5fa8;
    --accent-soft: #e5eef9;
    --good: #1d7a3e;
    --good-soft: #e3f3e8;
    --bad: #b3261e;
    --bad-soft: #fbe7e5;
    --warn-soft: #fdf3dc;
    --code-bg: #f4f2ec;
    --code-comment: #6b7a5c;
    --code-string: #9a4a12;
    --code-keyword: #6a2fa0;
  }
  @media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) {
      --bg: #161614;
      --surface: #1f1f1c;
      --surface-2: #262622;
      --text: #ecebe6;
      --muted: #a9a79f;
      --border: #3a3934;
      --accent: #7fb2ee;
      --accent-soft: #1c2b3d;
      --good: #6fcf8e;
      --good-soft: #17301f;
      --bad: #f19a92;
      --bad-soft: #3a1d1a;
      --warn-soft: #3a311a;
      --code-bg: #1a1a17;
      --code-comment: #98a886;
      --code-string: #e2a36b;
      --code-keyword: #c6a0f0;
      color-scheme: dark;
    }
  }
  :root[data-theme="dark"] {
    --bg: #161614;
    --surface: #1f1f1c;
    --surface-2: #262622;
    --text: #ecebe6;
    --muted: #a9a79f;
    --border: #3a3934;
    --accent: #7fb2ee;
    --accent-soft: #1c2b3d;
    --good: #6fcf8e;
    --good-soft: #17301f;
    --bad: #f19a92;
    --bad-soft: #3a1d1a;
    --warn-soft: #3a311a;
    --code-bg: #1a1a17;
    --code-comment: #98a886;
    --code-string: #e2a36b;
    --code-keyword: #c6a0f0;
    color-scheme: dark;
  }
  * { box-sizing: border-box; }
  @media (prefers-reduced-motion: no-preference) { html { scroll-behavior: smooth; } }
  :focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
`;

/** The page for the collected sections, at commit `sha`. */
export function page({classes, annotations, endpoints, logs, tables, settings}, sha) {
  const short = sha.slice(0, 7);
  const tokens = TOKENS;
  const BLOB = `https://github.com/Greywolves-se/rainmaker/blob/${sha}/`;
  const sorted = classes
    .map(c => ({...c, summary: c.summary.endsWith(':') ? c.summary + ' … (see the code)' : c.summary}))
    .sort((a, b) => APP_ORDER.indexOf(a.app) - APP_ORDER.indexOf(b.app) || a.name.localeCompare(b.name));
  const data = {classes: sorted, annotations, deps: dependencies.map(d => ({...d, url: d.link ? BLOB + d.link : null})), endpoints, logs, tables, settings};
  const json = value => JSON.stringify(value).replace(/</g, '\\u003c');
  const total = Object.values(data).reduce((sum, list) => sum + list.length, 0);

  const page = `<title>Rainmaker Code Index</title>
  <meta name="claude:viewport-fit-sides" content="cover">
  <style>${tokens}
    body { background: var(--bg); color: var(--text); font: 15px/1.55 system-ui, -apple-system, "Segoe UI", sans-serif; }
    .wrap { max-width: 1080px; margin: 0 auto; padding-block: 28px 80px; padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); }
    h1 { font-size: 28px; margin: 0 0 4px; text-wrap: balance; }
    .meta { color: var(--muted); margin: 0 0 16px; }
    a { color: var(--accent); }
    code { font-family: ui-monospace, "Cascadia Code", Consolas, monospace; font-size: .92em; background: var(--code-bg); padding: 1px 4px; border-radius: 4px; overflow-wrap: anywhere; }
    .note { background: var(--accent-soft); border-radius: 8px; padding: 10px 14px; margin: 0 0 8px; }
    .tabs { display: flex; gap: 8px; margin: 16px 0 12px; flex-wrap: wrap; }
    .tabs button, .filters button { font: inherit; padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border); background: var(--surface); color: var(--text); cursor: pointer; }
    .tabs button[aria-selected="true"], .filters button[aria-pressed="true"] { background: var(--accent); border-color: var(--accent); color: var(--surface); }
    .tabs .hits { font-size: 12px; font-weight: 600; margin-left: 6px; padding: 0 6px; border-radius: 999px; background: var(--surface-2); color: var(--muted); }
    .tabs button[aria-selected="true"] .hits { background: var(--surface); color: var(--accent); }
    .tabs button.none { opacity: .55; }
    .controls { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 2; background: var(--bg); padding: 10px 0; display: grid; gap: 8px; border-bottom: 1px solid var(--border); }
    input[type="search"] { font: inherit; width: 100%; box-sizing: border-box; padding: 9px 12px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface); color: var(--text); }
    .filters { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .filters:empty { display: none; }
    .filters button { padding: 3px 10px; font-size: 13px; }
    .filters .label { color: var(--muted); font-size: 13px; margin-right: 2px; }
    .filters label { font-size: 13px; color: var(--muted); display: inline-flex; gap: 6px; align-items: center; margin-left: 4px; }
    .count { color: var(--muted); font-size: 13px; }
    .list { display: grid; gap: 10px; margin-top: 14px; }
    .entry { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 12px 16px; min-width: 0; }
    .entry h3 { margin: 0; font-size: 16px; display: flex; flex-wrap: wrap; gap: 6px 10px; align-items: baseline; }
    .entry h3 .name { font-family: ui-monospace, "Cascadia Code", Consolas, monospace; overflow-wrap: anywhere; }
    .chip { font-size: 12px; font-weight: 600; padding: 0 8px; border-radius: 999px; border: 1px solid var(--border); background: var(--surface-2); color: var(--muted); white-space: nowrap; }
    .chip.app { background: var(--accent-soft); border-color: var(--accent); color: var(--accent); }
    .chip.good { background: var(--good-soft); border-color: var(--good); color: var(--good); }
    .chip.bad { background: var(--bad-soft); border-color: var(--bad); color: var(--bad); }
    .chip.warn { background: var(--warn-soft); border-color: #b07a10; color: #8a5a00; }
    :root[data-theme="dark"] .chip.warn { color: #f0c46a; border-color: #f0c46a; }
    @media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) .chip.warn { color: #f0c46a; border-color: #f0c46a; } }
    .entry p { margin: 6px 0 0; }
    .entry .where { color: var(--muted); font-size: 13px; overflow-wrap: anywhere; }
    .entry .links { font-size: 13px; margin-top: 6px; display: flex; gap: 14px; flex-wrap: wrap; }
    .entry .gotcha { margin-top: 6px; padding: 6px 10px; background: var(--warn-soft); border-radius: 6px; font-size: 14px; }
    .entry dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 4px 12px; margin: 8px 0 0; font-size: 14px; }
    .entry dt { color: var(--muted); }
    .entry dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
    .entry ul.cols { margin: 6px 0 0; padding-left: 18px; font-size: 14px; }
    .empty { color: var(--muted); padding: 24px 0; }
    @media (max-width: 520px) { .entry dl { grid-template-columns: minmax(0, 1fr); } .entry dt { margin-top: 4px; } }
  </style>

  <div class="wrap">
    <h1>Rainmaker code index</h1>
    <p class="meta">Classes, annotations, settings, endpoints, log messages, database tables and dependencies of G1–G3, rainmaker.js, the end-to-end test and the test site · <code>develop</code> at <code>${short}</code> · ${total} entries</p>
    <p class="note">A lookup, not a guide: type a name or a word, and the tabs show where it was found. Entries are generated from the code (Javadoc, mappings, log calls, migrations, settings files), with explanations added by hand where they matter. Links in an entry jump to related entries in other tabs. "Flow" links go to <a href="https://claude.ai/artifact/WKRoyhFKywZZmYVX4MmjPf">Rainmaker behind the scenes</a>; domain words are in the onboarding guide's glossary.</p>
    <div class="tabs" role="tablist" id="tabs"></div>
    <div class="controls">
      <input type="search" id="q" placeholder="Search everything… (e.g. click, retry, RAINMAKER_FAKE_ZONE, No widget links)" aria-label="Search everything">
      <div class="filters" id="filters-a"></div>
      <div class="filters" id="filters-b"></div>
      <div class="count" id="count" role="status" aria-live="polite"></div>
    </div>
    <div class="list" id="list"></div>
  </div>

  <script type="application/json" id="data">${json(data)}</script>
  <script>
    (function index() {
      const data = JSON.parse(document.getElementById('data').textContent);
      const el = id => document.getElementById(id);
      const esc = text => String(text ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
      const rich = text => esc(text).replace(/\`([^\`]+)\`/g, '<code>$1</code>').split('**').map((part, index) => index % 2 ? '<strong>' + part + '</strong>' : part).join('');
      const go = (tab, q, label) => '<a href="#" data-tab="' + tab + '" data-q="' + esc(q) + '">' + esc(label || q) + '</a>';
      const classLink = name => name ? go('classes', name) : '';
      const sorted = values => [...new Set(values.filter(Boolean))].sort();
      const levelChip = level => '<span class="chip ' + (/ERROR/.test(level) ? 'bad' : /WARN/.test(level) ? 'warn' : '') + '">' + esc(level) + '</span>';
      const QUIET = ['record', 'exception', 'enum'];

      const TABS = [
        {id: 'classes', label: 'Classes and files', items: data.classes,
          text: c => [c.name, c.summary, c.package, c.module, c.kind, c.path],
          filters: [['App', c => c.app, ['G1', 'G2', 'G3', 'Widget', 'E2E', 'Test site']], ['Kind', c => c.kind, sorted(data.classes.map(c => c.kind))]],
          quiet: true,
          render: c => '<h3><span class="name">' + esc(c.name) + '</span><span class="chip">' + esc(c.kind) + '</span><span class="chip app">' +
            esc(c.app === 'Test site' ? c.app : c.app + ' · ' + c.module) + '</span>' + (c.api ? '<span class="chip good">public api</span>' : '') + '</h3>' +
            '<p>' + rich(c.summary) + '</p>' + (c.package ? '<p class="where">' + esc(c.package) + '</p>' : '') +
            '<div class="links"><a href="' + esc(c.url) + '">Code</a>' + (c.guide ? '<a href="' + esc(c.guide) + '">Flow</a>' : '') + '</div>'},
        {id: 'annotations', label: 'Annotations', items: data.annotations,
          text: a => [a.name, a.group, a.what, a.how],
          filters: [['Group', a => a.group, [...new Set(data.annotations.map(a => a.group))]]],
          render: a => '<h3><span class="name">' + esc(a.name) + '</span><span class="chip">' + esc(a.group) + '</span><span class="chip app">' +
            a.uses + ' use' + (a.uses === 1 ? '' : 's') + ' in ' + a.files + ' file' + (a.files === 1 ? '' : 's') + (a.main ? '' : ', tests only') + '</span></h3>' +
            '<p>' + rich(a.what) + '</p><div class="gotcha"><strong>How we use it:</strong> ' + rich(a.how) + '</div>' +
            '<div class="links">Examples: ' + a.examples.map(x => '<a href="' + esc(x.url) + '">' + esc(x.label) + '</a>').join('') + '</div>'},
        {id: 'settings', label: 'Settings', items: data.settings,
          text: s => [s.key, s.value, s.note, s.owner, s.app, s.profile, (s.feeds || []).join(' '), (s.compose || []).join(' ')],
          filters: [['App', s => s.app.split(', ')[0], ['G1', 'G2', 'G3', 'Test site', 'PostgreSQL']], ['Kind', s => s.kind, ['property', 'environment variable']]],
          render: s => s.kind === 'property'
            ? '<h3><span class="name">' + esc(s.key) + '</span><span class="chip app">' + esc(s.app) + '</span>' +
              (s.profile ? '<span class="chip">' + esc(s.profile === 'default in code' ? s.profile : s.profile + ' profile') + '</span>' : '') +
              (s.checked ? '<span class="chip good">checked at start-up</span>' : '') + '</h3>' +
              '<dl><dt>Value</dt><dd><code>' + esc(s.value || '(empty)') + '</code></dd>' +
              (s.owner ? '<dt>Read by</dt><dd>' + classLink(s.owner) + '</dd>' : '') + '</dl>' +
              (s.note ? '<p>' + rich(s.note) + '</p>' : '') + '<div class="links"><a href="' + esc(s.url) + '">Where it is set</a></div>'
            : '<h3><span class="name">' + esc(s.key) + '</span><span class="chip">environment variable</span>' + (s.app ? '<span class="chip app">' + esc(s.app) + '</span>' : '') + '</h3>' +
              (s.note ? '<p>' + rich(s.note) + '</p>' : '') +
              '<dl>' + (s.feeds.length ? '<dt>Sets</dt><dd>' + s.feeds.map(key => go('settings', key)).join(', ') + '</dd>' : '') +
              (s.value ? '<dt>Default</dt><dd><code>' + esc(s.value) + '</code></dd>' : '') +
              (s.compose.length ? '<dt>Docker</dt><dd>' + s.compose.map(line => '<code>' + esc(line) + '</code>').join('<br>') + '</dd>' : '') +
              (s.example !== null ? '<dt>.env.example</dt><dd><code>' + esc(s.key + '=' + s.example) + '</code></dd>' : '') + '</dl>' +
              (s.url ? '<div class="links"><a href="' + esc(s.url) + '">In docker-compose</a></div>' : '')},
        {id: 'endpoints', label: 'Endpoints', items: data.endpoints,
          text: e => [e.method + ' ' + e.path, e.controller, e.handler, e.summary, e.callers, e.answers, e.app],
          filters: [['App', e => e.app, ['G1', 'G2', 'G3', 'Test site']], ['Method', e => e.method, sorted(data.endpoints.map(e => e.method))]],
          render: e => '<h3><span class="name">' + esc(e.method + ' ' + e.path) + '</span><span class="chip app">' + esc(e.app) + '</span>' +
            (e.contract ? '<span class="chip">contract ' + esc(e.contract) + '</span>' : '') + '</h3>' +
            (e.summary ? '<p>' + rich(e.summary) + '</p>' : '') +
            '<dl><dt>Handled by</dt><dd>' + classLink(e.controller) + (e.handler ? ' · <code>' + esc(e.handler) + '()</code>' : '') + '</dd>' +
            (e.callers ? '<dt>Called by</dt><dd>' + rich(e.callers) + '</dd>' : '') +
            (e.answers ? '<dt>Answers</dt><dd>' + rich(e.answers) + '</dd>' : '') + '</dl>' +
            '<div class="links"><a href="' + esc(e.url) + '">Code</a></div>'},
        {id: 'logs', label: 'Log messages', items: data.logs,
          text: l => [l.message, l.level, l.className, l.note, l.comment, l.app],
          filters: [['App', l => l.app, ['G1', 'G2', 'G3', 'Test site']], ['Level', l => l.level, sorted(data.logs.map(l => l.level))]],
          render: l => '<h3><span class="name">' + esc(l.message) + '</span>' + levelChip(l.level) + '<span class="chip app">' + esc(l.app + (l.app === 'Test site' ? '' : ' · ' + l.module)) + '</span></h3>' +
            (l.note ? '<p>' + rich(l.note) + '</p>' : '') +
            '<dl><dt>Written by</dt><dd>' + classLink(l.className) + '</dd>' + (l.comment ? '<dt>In the code</dt><dd>' + rich(l.comment) + '</dd>' : '') + '</dl>' +
            '<div class="links"><a href="' + esc(l.url) + '">Code</a></div>'},
        {id: 'tables', label: 'Tables', items: data.tables,
          text: t => [t.name, t.module, t.database, t.comment, t.columns.join(' '), t.usedBy.join(' ')],
          filters: [['App', t => t.app, ['G1', 'G2', 'G3']]],
          render: t => '<h3><span class="name">' + esc(t.name) + '</span><span class="chip app">' + esc(t.app + ' · ' + t.database) + '</span>' +
            (t.appendOnly ? '<span class="chip good">append-only (the database refuses UPDATE and DELETE)</span>' : '') + '</h3>' +
            (t.comment ? '<p>' + rich(t.comment) + '</p>' : '') +
            '<ul class="cols">' + t.columns.map(c => '<li><code>' + esc(c) + '</code></li>').join('') + '</ul>' +
            '<dl><dt>Created in</dt><dd><code>' + esc(t.created) + '</code>' + (t.altered.length ? ', changed in ' + t.altered.map(m => '<code>' + esc(m) + '</code>').join(', ') : '') + '</dd>' +
            (t.usedBy.length ? '<dt>Used by</dt><dd>' + t.usedBy.map(classLink).join(', ') + '</dd>' : '') + '</dl>' +
            '<div class="links"><a href="' + esc(t.url) + '">Migration</a></div>'},
        {id: 'deps', label: 'Dependencies', items: data.deps,
          text: d => [d.name, d.what, d.gotchas, d.where, d.group],
          filters: [['Kind', d => d.group, ['Runtime', 'Test', 'Build', 'Widget', 'E2E']]],
          render: d => '<h3><span>' + esc(d.name) + '</span><span class="chip">' + esc(d.group) + '</span><span class="chip">' + esc(d.version) + '</span></h3>' +
            '<p>' + rich(d.what) + '</p><p class="where">Used in: ' + esc(d.where) + '</p>' +
            (d.gotchas ? '<div class="gotcha"><strong>Good to know:</strong> ' + rich(d.gotchas) + '</div>' : '') +
            (d.url ? '<div class="links"><a href="' + esc(d.url) + '">Where it is declared</a></div>' : '')},
      ];

      const state = {tab: 'classes', q: '', picks: [null, null], quiet: false};
      try { const saved = JSON.parse(localStorage.getItem('rmCodeIndex') || '{}'); if (TABS.some(t => t.id === saved.tab)) state.tab = saved.tab; } catch (e) {}
      const matches = (tab, item) => {
        const words = state.q.toLowerCase().split(/\\s+/).filter(Boolean);
        const hay = tab.text(item).join(' ').toLowerCase();
        return words.every(word => hay.includes(word));
      };
      function filterRow(container, tab, index) {
        const filter = tab.filters[index];
        if (!filter) { container.innerHTML = ''; return; }
        const [label, , values] = filter;
        container.innerHTML = '<span class="label">' + esc(label) + '</span>' + ['All'].concat(values).map(value =>
          '<button type="button" data-pick="' + esc(value) + '" aria-pressed="' + String(value === 'All' ? state.picks[index] === null : state.picks[index] === value) + '">' + esc(value) + '</button>').join('');
        container.querySelectorAll('button').forEach(button => button.addEventListener('click', () => {
          const value = button.getAttribute('data-pick');
          state.picks[index] = value === 'All' ? null : value;
          render();
        }));
        if (tab.quiet) {
          const quiet = document.createElement('label');
          quiet.innerHTML = '<input type="checkbox"' + (state.quiet ? ' checked' : '') + '> hide records, exceptions and enums';
          quiet.querySelector('input').addEventListener('change', event => { state.quiet = event.target.checked; render(); });
          if (index === 1) container.appendChild(quiet);
        }
      }
      function render() {
        try { localStorage.setItem('rmCodeIndex', JSON.stringify({tab: state.tab})); } catch (e) {}
        el('tabs').innerHTML = TABS.map(tab => {
          const hits = state.q ? tab.items.filter(item => matches(tab, item)).length : null;
          return '<button type="button" role="tab" data-switch="' + tab.id + '" aria-selected="' + String(tab.id === state.tab) + '"' +
            (hits === 0 ? ' class="none"' : '') + '>' + esc(tab.label) + (hits === null ? '' : '<span class="hits">' + hits + '</span>') + '</button>';
        }).join('');
        el('tabs').querySelectorAll('button').forEach(button => button.addEventListener('click', () => {
          state.tab = button.getAttribute('data-switch');
          state.picks = [null, null];
          render();
        }));
        const tab = TABS.find(t => t.id === state.tab);
        filterRow(el('filters-a'), tab, 0);
        filterRow(el('filters-b'), tab, 1);
        const shown = tab.items.filter(item =>
          tab.filters.every((filter, index) => state.picks[index] === null || filter[1](item) === state.picks[index]) &&
          (!tab.quiet || !state.quiet || state.q || state.picks[1] || !QUIET.includes(item.kind)) &&
          matches(tab, item));
        // An entry whose name is the search comes first, then names that contain it, then the rest.
        const query = state.q.trim().toLowerCase();
        const rank = item => {
          const name = String(tab.text(item)[0]).toLowerCase();
          return !query ? 0 : name === query ? 0 : name.includes(query) ? 1 : 2;
        };
        if (query) shown.sort((a, b) => rank(a) - rank(b));
        el('count').textContent = shown.length + ' of ' + tab.items.length + ' ' + tab.label.toLowerCase();
        el('list').innerHTML = shown.map(item => '<article class="entry">' + tab.render(item) + '</article>').join('') ||
          '<p class="empty">Nothing matches here. The numbers on the tabs show where the search does match.</p>';
      }
      // A link to another entry: switch tab and search for it.
      document.addEventListener('click', event => {
        const link = event.target.closest('a[data-tab]');
        if (!link) return;
        event.preventDefault();
        state.tab = link.getAttribute('data-tab');
        state.q = link.getAttribute('data-q');
        state.picks = [null, null];
        el('q').value = state.q;
        render();
        window.scrollTo({top: 0});
      });
      el('q').addEventListener('input', event => { state.q = event.target.value; render(); });
      render();
    })();
  </script>
  `;
  return page;
}
