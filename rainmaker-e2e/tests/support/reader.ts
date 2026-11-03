/**
 * What a reader does on the test site. The step that makes the widget show the commercial next
 * step is in one function, showNextStep, so only it changes when the reader flow does (#154).
 */
import {type Locator, type Page, type Response} from '@playwright/test';

/** Opens the article and answers the consent banner with "necessary only", if it shows. */
export async function openArticle(page: Page, url: string): Promise<void> {
  await page.goto(url, {waitUntil: 'load'});
  const necessaryOnly = page.locator('[data-consent-choice="necessary"]');
  if (await necessaryOnly.isVisible()) {
    await necessaryOnly.click();
  }
}

/**
 * Makes the widget show the next step, and returns G1's answer to its links request
 * (`/widget/v1/links`, contract 7.4). Waiting for that answer means the widget has really decided,
 * so a test that expects no panel can't pass too early.
 *
 * Since #220 (ADR 0005) the widget asks only when the reader leaves the article: after qualified
 * reading (active time, with at least one scroll; 2 s in e2e.env instead of 60 s) and once the
 * article's end, [data-rainmaker-article-end], has been reached. Scrolling the end into view does
 * both: it is the scroll, and it reaches the end. The 2 s pass while this waits for the answer.
 * The test's articles are long enough that the end starts out of view.
 */
export async function showNextStep(page: Page): Promise<Response> {
  const answered = page.waitForResponse(response =>
    response.url().includes('/widget/v1/links')
  );
  await page.locator('[data-rainmaker-article-end]').scrollIntoViewIfNeeded();
  return answered;
}

/** The widget's panel: one element with an open Shadow DOM, which Playwright's locators reach. */
export function panel(page: Page): Locator {
  return page.locator('[data-rainmaker-panel]');
}

/** The place beside the article, where the next step shows at desktop width (#220). */
export function sidebar(page: Page): Locator {
  return page.locator('[data-rainmaker-next-step="sidebar"]');
}
