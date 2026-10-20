// Smoke tests (#29): the built app with the shared sample files, on desktop and mobile
// (playwright.config.ts). They check that every page loads, the menu works and the language
// switch works.
import {expect, test, type Page} from '@playwright/test';
import {serveSampleData} from './sampleData';

const PAGES = [
  {segment: 'overview', title: 'Overview'},
  {segment: 'development', title: 'Development'},
  {segment: 'sources', title: 'Sources'},
  {segment: 'start-here', title: 'START HERE'},
];

const heading = (page: Page) => page.getByRole('heading', {level: 1});
const menu = (page: Page) => page.getByRole('navigation', {name: 'Main menu'});

/** The page loaded: its own title, not an error title, and no error in the page content. */
async function expectPageLoaded(page: Page, title: string) {
  await expect(heading(page)).toHaveText(title);
  await expect(page.getByRole('main').getByRole('alert')).toHaveCount(0);
}

let pageErrors: Error[];

test.beforeEach(async ({page}) => {
  pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error));
  await serveSampleData(page);
});

test.afterEach(() => {
  // An uncaught exception fails the test even when the page looks right.
  expect(pageErrors).toEqual([]);
});

test.describe('in English', () => {
  test.beforeEach(async ({page}) => {
    await page.addInitScript(() =>
      localStorage.setItem('missionControl.language', 'en')
    );
  });

  for (const {segment, title} of PAGES) {
    test(`${title} loads without the error state`, async ({page}) => {
      await page.goto(`/rainmaker/${segment}`);

      await expectPageLoaded(page, title);
    });
  }

  test('START HERE shows the report', async ({page}) => {
    await page.goto('/rainmaker/start-here');

    await expect(
      page.getByRole('heading', {name: 'START HERE – Example project'})
    ).toBeVisible();
  });

  test('the menu opens every page and the other project', async ({page}) => {
    await page.goto('/');
    await expect(page).toHaveURL('/rainmaker/overview');
    await expectPageLoaded(page, 'Overview');

    for (const {segment, title} of PAGES.slice(1)) {
      await menu(page).getByRole('link', {name: title}).click();
      await expect(page).toHaveURL(`/rainmaker/${segment}`);
      await expectPageLoaded(page, title);
    }

    // A project link keeps the page you are on.
    await menu(page).getByRole('link', {name: 'Mission Control'}).click();
    await expect(page).toHaveURL('/mission-control/start-here');
    await expectPageLoaded(page, 'START HERE');
  });
});

test('the language switch changes the texts and is remembered', async ({
  page,
}) => {
  await page.goto('/rainmaker/overview');
  await expectPageLoaded(page, 'Översikt');

  await page.getByRole('button', {name: 'English'}).click();
  await expectPageLoaded(page, 'Overview');
  await expect(
    menu(page).getByRole('link', {name: 'Development'})
  ).toBeVisible();

  await page.reload();
  await expectPageLoaded(page, 'Overview');
});
