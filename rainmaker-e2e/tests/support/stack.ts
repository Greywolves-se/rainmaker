/**
 * The local stack (#146) as the test sees it: publishing articles on the test site, reading what
 * G3 and the click database say, and stopping and starting Rainmaker's apps.
 */
import {execFileSync} from 'node:child_process';
import {rmSync, writeFileSync} from 'node:fs';
import {join, resolve} from 'node:path';
import {type APIRequestContext, expect} from '@playwright/test';

/** The repository root, where docker-compose.yml is. */
const ROOT = resolve(import.meta.dirname, '..', '..', '..');
const ARTICLES = join(ROOT, 'test-site', 'content', 'articles');

/** The same marker as RAINMAKER_FAKE_GREEN_MARKER in e2e.env: the fake makes the headline green. */
export const GREEN_MARKER = '[e2e-green]';

export const SITE = 'http://localhost:3000';
export const EDITOR_VIEW = 'http://localhost:8083/publisher/articles';
const APPROVED_LINKS = 'http://localhost:8083/api/approved-links';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** One article this test published. */
export interface PublishedArticle {
  headline: string;
  url: string;
}

const published: string[] = [];

/**
 * Publishes a new article on the test site the way #159 does it: a new file in
 * test-site/content/articles/. Both articles are about a bathroom, so the fake's topic ("home")
 * matches S4's offers; only the marker decides the zone.
 */
export function publishArticle(zone: 'green' | 'red'): PublishedArticle {
  const run = Date.now().toString(36);
  const slug = `e2e-${run}-${zone === 'green' ? 'badrum' : 'brand'}`;
  const headline =
    zone === 'green'
      ? `Så lyckas du med badrumsrenoveringen ${GREEN_MARKER}`
      : 'Två skadade när badrumsrenovering slutade i brand';
  const article = {
    id: slug,
    slug,
    section: 'bostad',
    expectedZone: zone,
    premium: false,
    kicker: 'BOSTAD',
    headline,
    lead: 'Ett testreportage från Rainmakers end-to-end-test (#154).',
    author: {name: 'Testredaktionen', role: 'End-to-end-test'},
    publishedAt: new Date().toISOString(),
    updatedAt: null,
    body: [{type: 'paragraph', text: 'Kakel, golvvärme och tätskikt: det här är testets text.'}],
  };
  const file = join(ARTICLES, `${slug}.json`);
  writeFileSync(file, JSON.stringify(article, null, 2) + '\n', 'utf8');
  published.push(file);
  return {headline, url: `${SITE}/artikel/${slug}`};
}

/** Removes the files this run published, so `git status` stays clean. G2 and G3 keep their copies. */
export function removePublishedArticles(): void {
  for (const file of published.splice(0)) {
    rmSync(file, {force: true});
  }
}

/**
 * Fails at once when the stack wasn't started with e2e.env, instead of after minutes of waiting
 * for a green article that never comes. The test site's widget tag shows the e2e reading seconds.
 */
export async function expectStackStartedForTheTest(request: APIRequestContext): Promise<void> {
  const page = await request.get(`${SITE}/artikel/mallorca-hosten-okanda-parlor`).catch(() => null);
  const started = page?.ok() && (await page.text()).includes('data-qualified-reading-seconds="2"');
  if (!started) {
    throw new Error(
      'Start the stack for the test first, from the repository root: ' +
        'docker compose --env-file rainmaker-e2e/e2e.env up --build -d (see README.md)'
    );
  }
}

/** The article's id, from G3's approved links API (contract 7.3). */
export async function articleIdAt(request: APIRequestContext, url: string): Promise<string> {
  const answer = await request.get(APPROVED_LINKS, {params: {url}});
  expect(answer.ok()).toBe(true);
  const articleId: unknown = (await answer.json()).articleId;
  if (typeof articleId !== 'string' || !UUID.test(articleId)) {
    throw new Error(`G3 gave no article id for ${url}`);
  }
  return articleId;
}

/** How many clicks S2 stored for the article, read with psql inside the Postgres container. */
export function clicksFor(articleId: string): number {
  if (!UUID.test(articleId)) {
    throw new Error(`Not an article id: ${articleId}`);
  }
  const count = compose(
    'exec',
    '-T',
    'postgres',
    'psql',
    '-U',
    'postgres',
    '-d',
    'tracking_click',
    '-tAc',
    `SELECT count(*) FROM click WHERE article_id = '${articleId}'`
  );
  return Number(count.trim());
}

/** Rainmaker's three apps; the test site and Postgres keep running. */
const RAINMAKER = ['g1', 'g2', 'g3'];

export function stopRainmaker(): void {
  compose('stop', ...RAINMAKER);
}

/** Starts the apps again and waits until each answers, so a later run finds the stack as before. */
export async function startRainmaker(request: APIRequestContext): Promise<void> {
  compose('start', ...RAINMAKER);
  const answers = [
    'http://localhost:8081/widget/rainmaker.js',
    'http://localhost:8082/api/articles?recent=1',
    EDITOR_VIEW,
  ];
  for (const url of answers) {
    await expect
      .poll(async () => (await request.get(url).catch(() => null))?.status(), {timeout: 120_000})
      .toBe(200);
  }
}

function compose(...args: string[]): string {
  return execFileSync('docker', ['compose', ...args], {cwd: ROOT, encoding: 'utf8'});
}
