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
 * Today the widget opens its panel when the reader hovers the headline (#152). Once #220 (PR #234)
 * replaces the hover, this becomes: scroll a little, wait the qualified reading
 * (RAINMAKER_QUALIFIED_READING_SECONDS, 2 s in e2e.env), and scroll [data-rainmaker-article-end]
 * into view (Gabriel's comment on #154).
 */
export async function showNextStep(page: Page): Promise<Response> {
  const answered = page.waitForResponse(response =>
    response.url().includes('/widget/v1/links')
  );
  await page.locator('[data-rainmaker-headline]').hover();
  return answered;
}

/** The widget's panel: one element with an open Shadow DOM, which Playwright's locators reach. */
export function panel(page: Page): Locator {
  return page.locator('[data-rainmaker-panel]');
}
