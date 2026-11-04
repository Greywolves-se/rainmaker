// The "Classes and files" section: one entry per Java class (its Javadoc summary, its kind from
// annotations and naming) and per widget or end-to-end TypeScript file (its JSDoc).
import {readFileSync, readdirSync, statSync} from 'node:fs';
import {join, relative, sep} from 'node:path';

/** "Flow" links point to the chapter of the team guide "Rainmaker behind the scenes" (in the hub). */
const GUIDE = 'https://claude.ai/artifact/WKRoyhFKywZZmYVX4MmjPf';

// ---------- Hand-written: which app each module belongs to, and its guide chapter.
const MODULES = {
  'content-ingestion': {app: 'G2', label: 'S5 content-ingestion', chapter: 'publish'},
  'content-intent': {app: 'G2', label: 'S6 content-intent', chapter: 'classify'},
  'matching-ranking': {app: 'G2', label: 'S7 matching-ranking', chapter: 'match'},
  'g2-content-app': {app: 'G2', label: 'G2 app', chapter: 'match'},
  'publisher-control': {app: 'G3', label: 'S3 publisher-control', chapter: 'editor'},
  'advertiser-marketplace': {app: 'G3', label: 'S4 advertiser-marketplace', chapter: 'match'},
  'g3-publisher-app': {app: 'G3', label: 'G3 app', chapter: 'editor'},
  'widget-delivery': {app: 'G1', label: 'S1 widget-delivery', chapter: 'nextstep'},
  'tracking-click': {app: 'G1', label: 'S2 tracking-click', chapter: 'click'},
  'g1-reader-app': {app: 'G1', label: 'G1 app', chapter: 'nextstep'},
  'test-site': {app: 'Test site', label: 'test-site', chapter: 'publish'},
};
// Classes whose chapter differs from their module's.
const CHAPTER_BY_PACKAGE = [
  [/publishercontrol\.links/, 'nextstep'],
  [/advertisermarketplace/, 'match'],
];

const WIDGET_CHAPTER = {
  'main.ts': 'read', 'start.ts': 'read', 'page.ts': 'read', 'qualified-reading.ts': 'read',
  'leaving-article.ts': 'read', 'fail-open.ts': 'read', 'links-api.ts': 'nextstep',
  'next-step-place.ts': 'nextstep', 'panel.ts': 'nextstep', 'translations.ts': 'nextstep',
};

/** The class and file entries of the checkout at `root`, linked to the code at commit `sha`. */
export function classes(root, sha) {
  const BLOB = `https://github.com/Greywolves-se/rainmaker/blob/${sha}/`;

  function files(dir, test) {
    const found = [];
    for (const name of readdirSync(dir)) {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) found.push(...files(path, test));
      else if (test(path)) found.push(path);
    }
    return found;
  }

  /** Javadoc or JSDoc text to one short summary: the first paragraph, at most ~320 characters. */
  function summarise(doc) {
    let text = doc
      .replace(/^\/\*\*|\*\/$/g, '')
      .split('\n')
      .map(line => line.replace(/^\s*\* ?/, ''))
      .join('\n');
    text = text.split(/\n\s*\n|<p>|<ul>|<ol>|\n\s*@/)[0];
    // Inline tags first, counting braces ({@code /c/{token}}), with the code's angle brackets
    // kept aside so the HTML stripping below doesn't eat them.
    let plain = '';
    for (let i = 0; i < text.length; i++) {
      const tag = /^\{@(code|link|linkplain|literal)\s+/.exec(text.slice(i));
      if (!tag) {
        plain += text[i];
        continue;
      }
      let depth = 1;
      let j = i + tag[0].length;
      for (; j < text.length && depth > 0; j++) {
        if (text[j] === '{') depth++;
        if (text[j] === '}') depth--;
      }
      let inner = text.slice(i + tag[0].length, j - 1).trim();
      if (tag[1].startsWith('link')) inner = inner.split(/\s+/)[0].replace(/^#/, '');
      plain += '`' + inner.replace(/</g, '‹').replace(/>/g, '›') + '`';
      i = j - 1;
    }
    text = plain
      .replace(/<[^>]+>/g, '')
      .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
      .replace(/‹/g, '<').replace(/›/g, '>')
      .replace(/\s+/g, ' ')
      .trim();
    if (text.length > 320) {
      const cut = text.slice(0, 320).lastIndexOf('. ');
      text = cut > 80 ? text.slice(0, cut + 1) : text.slice(0, 317) + '…';
    }
    return text;
  }

  function javaEntry(path) {
    const source = readFileSync(path, 'utf8');
    const rel = relative(root, path).split(sep).join('/');
    const decl = /^(public\s+)?((?:final|abstract|sealed|non-sealed)\s+)*(class|record|interface|enum|@interface)\s+(\w+)/m.exec(source);
    if (!decl) return null;
    const before = source.slice(0, decl.index);
    const docStart = before.lastIndexOf('/**');
    const docEnd = before.lastIndexOf('*/');
    const doc = docStart >= 0 && docEnd > docStart ? before.slice(docStart, docEnd + 2) : '';
    const annotations = [...before.slice(docEnd > 0 ? docEnd : 0).matchAll(/@(\w+)/g)].map(m => m[1]);
    const pkg = (source.match(/^package ([\w.]+);/m) || [])[1] || '';
    const module = rel.startsWith('test-site/') ? 'test-site' : rel.split('/')[1];
    const name = decl[4];
    const type = decl[3];
    const has = text => source.includes(text);
    // Only the class's own declaration counts for "extends": a generic like
    // `Class<? extends SdkException>` elsewhere in the file doesn't make it an exception.
    const declaration = source.slice(decl.index, source.indexOf('{', decl.index));
    let kind;
    if (/Application$/.test(name) && has('SpringApplication.run')) kind = 'app';
    else if (annotations.includes('RestController') || annotations.includes('Controller')) kind = 'controller';
    else if (annotations.includes('ConfigurationProperties')) kind = 'settings';
    else if (annotations.includes('Configuration')) kind = 'configuration';
    else if (has('@TransactionalEventListener') || has('@EventListener')) kind = 'event listener';
    else if ((/implements Runnable/.test(source) && /Retry/.test(name)) || /(Sync|Ingestion)$/.test(name)) kind = 'scheduled job';
    else if (/Parser$/.test(name)) kind = 'parser';
    else if (/(Filter|Advice)$/.test(name)) kind = 'web filter';
    else if (/Client$/.test(name)) kind = 'client';
    else if (/extends \w*(Exception|Error)\b/.test(declaration)) kind = 'exception';
    else if (/(Store|Repository|Versions|Results)$/.test(name) || /^Jdbc/.test(name)) kind = 'store';
    else if (type === 'interface') kind = 'interface';
    else if (type === 'enum') kind = 'enum';
    else if (type === 'record') kind = 'record';
    else kind = 'class';
    const meta = MODULES[module];
    let chapter = meta.chapter;
    for (const [pattern, other] of CHAPTER_BY_PACKAGE) if (pattern.test(pkg)) chapter = other;
    const line = source.slice(0, docStart >= 0 ? docStart : decl.index).split('\n').length;
    return {
      name, kind, app: meta.app, module: meta.label, package: pkg,
      api: /\.api(\.|$)/.test(pkg), public: Boolean(decl[1]),
      summary: summarise(doc) || '(no Javadoc summary)',
      url: `${BLOB}${rel}#L${line}`, path: rel,
      guide: module === 'test-site' ? null : `${GUIDE}#${chapter}`,
    };
  }

  function tsEntry(path, app, label) {
    const source = readFileSync(path, 'utf8');
    const rel = relative(root, path).split(sep).join('/');
    const file = rel.split('/').pop();
    const exports = [...source.matchAll(/^export (?:async )?(?:function|const|class|interface|type) (\w+)/gm)].map(m => m[1]);
    // The JSDoc right before the first exported function, else the file's first JSDoc.
    const firstFn = /^export (?:async )?function \w+/m.exec(source);
    let doc = '';
    if (firstFn) {
      const before = source.slice(0, firstFn.index);
      const start = before.lastIndexOf('/**');
      const end = before.lastIndexOf('*/');
      if (start >= 0 && end > start && before.slice(end + 2).trim() === '') doc = before.slice(start, end + 2);
    }
    if (!doc) doc = (source.match(/\/\*\*[\s\S]*?\*\//) || [''])[0];
    return {
      name: file, kind: app === 'Widget' ? 'widget module' : 'end-to-end test', app, module: label,
      package: exports.length ? 'exports ' + exports.join(', ') : '', api: false, public: true,
      summary: summarise(doc) || '(no summary)', url: `${BLOB}${rel}`, path: rel,
      guide: WIDGET_CHAPTER[file] ? `${GUIDE}#${WIDGET_CHAPTER[file]}` : null,
    };
  }

  const java = [
    ...readdirSync(join(root, 'services')).filter(m => MODULES[m]).flatMap(m => files(join(root, 'services', m, 'src', 'main', 'java'), p => p.endsWith('.java'))),
    ...['g1-reader-app', 'g2-content-app', 'g3-publisher-app'].flatMap(m => files(join(root, 'apps', m, 'src', 'main', 'java'), p => p.endsWith('.java'))),
    ...files(join(root, 'test-site', 'src', 'main', 'java'), p => p.endsWith('.java')),
  ].filter(p => !p.endsWith('package-info.java'));
  const entries = [
    ...java.map(javaEntry).filter(Boolean),
    ...files(join(root, 'widget', 'src'), p => p.endsWith('.ts') && !p.endsWith('.test.ts')).map(p => tsEntry(p, 'Widget', 'widget (rainmaker.js)')),
    ...files(join(root, 'rainmaker-e2e', 'tests'), p => p.endsWith('.ts')).map(p => tsEntry(p, 'E2E', 'rainmaker-e2e')),
  ];
  entries.sort((a, b) => a.name.localeCompare(b.name));
  return entries;
}
