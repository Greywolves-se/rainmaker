// Builds the Rainmaker code index: one searchable HTML page with the classes, annotations, settings,
// endpoints, log messages, database tables and dependencies of the checkout it runs in (#253).
//
//   node docs/tools/code-index/index.mjs [--out <file>]
//
// Reads the working tree, links to the code at the current commit, and writes
// build/code-index/index.html (ignored by git) unless --out says otherwise. Node's built-in modules
// only. See README.md for how to add a note and how to publish the page.
import {execFileSync} from 'node:child_process';
import {mkdirSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {annotations} from './lib/annotations.mjs';
import {classes} from './lib/classes.mjs';
import {extract} from './lib/extract.mjs';
import {page} from './lib/page.mjs';

/** The repository root: three folders up from this file. */
export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

/** Every section of the index for the checkout at `root`, linked to its current commit. */
export function collect(root = ROOT) {
  const sha = execFileSync('git', ['rev-parse', 'HEAD'], {cwd: root, encoding: 'utf8'}).trim();
  const annotationSection = annotations(root, sha);
  return {
    sha,
    classes: classes(root, sha),
    annotations: annotationSection.entries,
    unexplainedAnnotations: annotationSection.unexplained,
    ...extract(root, sha),
  };
}

/** What still needs a hand-written note: the test fails while any of these lists is not empty. */
export function missingNotes(index) {
  return {
    annotations: index.unexplainedAnnotations,
    logs: index.logs.filter(log => /WARN|ERROR/.test(log.level) && !log.note).map(log => `${log.className}: ${log.message}`),
    endpoints: index.endpoints.filter(e => e.app !== 'Test site' && !(e.callers && e.answers)).map(e => `${e.method} ${e.path}`),
  };
}

function main() {
  const at = process.argv.indexOf('--out');
  const out = at > 0 ? resolve(process.argv[at + 1]) : join(ROOT, 'build', 'code-index', 'index.html');
  const index = collect();
  mkdirSync(dirname(out), {recursive: true});
  writeFileSync(out, page(index, index.sha));
  const counts = ['classes', 'annotations', 'settings', 'endpoints', 'logs', 'tables'].map(name => `${index[name].length} ${name}`);
  console.log(`Code index for ${index.sha.slice(0, 7)}: ${counts.join(', ')}`);
  console.log(`Written to ${out}`);
  for (const [section, missing] of Object.entries(missingNotes(index))) {
    if (missing.length) console.log(`Needs a note (${section}): ${missing.join(' | ')}`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
