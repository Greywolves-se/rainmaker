// Tests of the code index generator (#253): run with `node --test docs/tools/code-index/test/`.
// The first test reads this checkout, so it fails when new code lacks its hand-written note.
import assert from 'node:assert/strict';
import {test} from 'node:test';
import {collect, missingNotes} from '../index.mjs';
import {page} from '../lib/page.mjs';

const index = collect();

test('every annotation, WARN/ERROR log line and G1–G3 endpoint in the code has its note', () => {
  assert.deepEqual(missingNotes(index), {annotations: [], logs: [], endpoints: []});
});

test('every section finds something in this checkout', () => {
  for (const section of ['classes', 'annotations', 'settings', 'endpoints', 'logs', 'tables']) {
    assert.ok(index[section].length > 0, `${section} is empty`);
  }
});

test('the generated parts match the code in known places', () => {
  const click = index.endpoints.find(e => e.method === 'HEAD' && e.path === '/c/{token}');
  assert.equal(click?.controller, 'ClickRedirectController');
  assert.equal(index.endpoints.find(e => e.controller === 'LandingPageController')?.path, '/landing/{offerId}');
  assert.deepEqual(index.tables.filter(t => t.appendOnly).map(t => t.name).sort(), ['click', 'suggestion_audit']);
  assert.equal(index.classes.find(c => c.name === 'BedrockArticleClassifier')?.kind, 'class');
  assert.equal(index.settings.find(s => s.key === 'PORT')?.app, 'Test site');
  assert.ok(index.tables.every(t => !t.url.includes('/target/')), 'a table links to a build copy');
});

test('a missing note is reported', () => {
  const lacking = {
    unexplainedAnnotations: ['NewAnnotation'],
    logs: [{level: 'WARN', note: '', className: 'Some', message: 'Something new'}, {level: 'INFO', note: '', className: 'Some', message: 'Fine'}],
    endpoints: [{app: 'G1', method: 'GET', path: '/new'}, {app: 'Test site', method: 'GET', path: '/page'}],
  };
  assert.deepEqual(missingNotes(lacking), {annotations: ['NewAnnotation'], logs: ['Some: Something new'], endpoints: ['GET /new']});
});

test('the page holds every section and the commit', () => {
  const html = page(index, index.sha);
  assert.match(html, /<title>Rainmaker Code Index<\/title>/);
  assert.ok(html.includes(index.sha.slice(0, 7)));
  for (const tab of ['classes', 'annotations', 'settings', 'endpoints', 'logs', 'tables', 'deps']) {
    assert.ok(html.includes(`id: '${tab}'`), `no ${tab} tab`);
  }
  assert.ok(!html.includes('</script><'.repeat(2)), 'data must not end the script early');
});
