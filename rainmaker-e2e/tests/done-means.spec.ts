/**
 * The demo's "done means" points (handledare's mail, 20 Oct 2026; #154) on the local stack: the
 * test site, G1, G2 with the fake classifier, G3 and Postgres. Each test publishes its own article,
 * so they don't depend on each other or on earlier runs. The last one stops Rainmaker and starts
 * it again.
 */
import {expect, test, type Locator, type Page} from '@playwright/test';
import {openArticle, panel, showNextStep, sidebar} from './support/reader';
import {
  EDITOR_VIEW,
  SITE,
  articleIdAt,
  clicksFor,
  expectStackStartedForTheTest,
  publishArticle,
  removePublishedArticles,
  startRainmaker,
  stopRainmaker,
} from './support/stack';

/**
 * From a new file to G3's editor view: G2 reads the feed (5 s in e2e.env), classifies and matches,
 * and G3 polls G2 (5 s). Generous, for a stack that has only just started.
 */
const PIPELINE_MS = 120_000;

test.beforeAll(async ({request}) => expectStackStartedForTheTest(request));
test.afterAll(removePublishedArticles);

/**
 * The article's entry in the editor view, once G3 has it; the page is reloaded until then. Found
 * by its address, which is new for every run: G3 keeps earlier runs' articles, with the same
 * headlines.
 */
async function editorEntry(page: Page, url: string, ready: (entry: Locator) => Locator) {
  const entry = page.locator('article').filter({has: page.locator(`h2 a[href="${url}"]`)});
  await expect(async () => {
    await page.goto(EDITOR_VIEW);
    await expect(ready(entry)).toBeVisible({timeout: 1_000});
  }).toPass({timeout: PIPELINE_MS});
  return entry;
}

test('a green article: editor view, approval, panel, and a click stored once', async ({
  page,
  request,
}) => {
  test.setTimeout(PIPELINE_MS + 60_000);
  const article = publishArticle('green');
  let advertiser = '';

  await test.step('a new article published on the site shows up in the editor view', async () => {
    const entry = await editorEntry(page, article.url, found =>
      found.getByRole('button', {name: 'Godkänn'}).first()
    );
    const suggestion = entry.locator('li').first();
    advertiser = (await suggestion.locator('strong').first().textContent()) ?? '';
    expect(advertiser).not.toBe('');

    // The decision updates its line in place (#269): no reload, no scroll. A reload would drop the
    // mark set here, and the scroll position is taken after Playwright has scrolled the button
    // into view, just before the click.
    const approve = suggestion.getByRole('button', {name: 'Godkänn'});
    await approve.scrollIntoViewIfNeeded();
    const before = await page.evaluate(() => {
      (window as Window & {notReloaded?: boolean}).notReloaded = true;
      return window.scrollY;
    });
    await approve.click();
    await expect(entry.locator('li').first()).toContainText('Godkänd');
    const after = await page.evaluate(() => ({
      notReloaded: (window as Window & {notReloaded?: boolean}).notReloaded === true,
      scrollY: window.scrollY,
    }));
    expect(after).toEqual({notReloaded: true, scrollY: before});
  });

  await test.step('after approval, the article shows the panel with the approved link', async () => {
    await openArticle(page, article.url);
    const answer = await showNextStep(page);
    expect(answer.status()).toBe(200);
    await expect(panel(page).getByRole('link')).toHaveCount(1);
    await expect(panel(page).getByRole('link')).toContainText(advertiser);
    // At desktop width the next step goes beside the article, never over it (#220).
    await expect(sidebar(page).locator('[data-rainmaker-panel]')).toHaveCount(1);
  });

  await test.step("a click lands on the offer's page and is stored once, also when clicked twice", async () => {
    const clickUrl = await panel(page).getByRole('link').getAttribute('href');
    expect(clickUrl).toMatch(/^http:\/\/localhost:8081\//);

    await panel(page).getByRole('link').click();
    await expect(page).toHaveURL(/^http:\/\/localhost:8083\/landing\//);
    await expect(page.getByText(advertiser).first()).toBeVisible();
    // The same click link again: a double-click, or the browser retrying it.
    await page.goto(clickUrl!);
    await expect(page).toHaveURL(/^http:\/\/localhost:8083\/landing\//);

    expect(clicksFor(await articleIdAt(request, article.url))).toBe(1);
  });
});

test('a red article never shows a panel, even when offers would match', async ({page}) => {
  test.setTimeout(PIPELINE_MS + 30_000);
  // About a bathroom like the green one, so its topic matches S4's offers; the zone is red.
  const article = publishArticle('red');

  const entry = await editorEntry(page, article.url, found => found.getByText('Röd'));
  await expect(entry).toContainText('Inga förslag');
  await expect(entry.getByRole('button')).toHaveCount(0);

  await openArticle(page, article.url);
  const answer = await showNextStep(page);
  expect(answer.status()).toBe(200);
  expect((await answer.json()).links).toEqual([]);
  await expect(panel(page)).toHaveCount(0);
});

test('with Rainmaker stopped, the article page still loads normally', async ({page, request}) => {
  test.setTimeout(180_000);
  const errors: Error[] = [];
  page.on('pageerror', error => errors.push(error));

  stopRainmaker();
  try {
    const response = await page.goto(`${SITE}/artikel/mallorca-hosten-okanda-parlor`);
    expect(response?.status()).toBe(200);
    await expect(page.locator('[data-rainmaker-headline]')).toContainText('Mallorca');
    await expect(page.locator('.article__body, article p').first()).toBeVisible();
    expect(errors).toEqual([]);
  } finally {
    await startRainmaker(request);
  }
});
