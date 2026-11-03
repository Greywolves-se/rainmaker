import {defineConfig, devices} from '@playwright/test';

/**
 * The demo's "done means" points against the running local stack (#154): start it first, as
 * README.md says. One worker, because the tests share the stack and one of them stops Rainmaker.
 * Chromium at desktop size, where the next step shows beside the article (#220); the narrow-screen
 * place is covered by the widget's own tests (next-step-place.test.ts).
 */
export default defineConfig({
  testDir: 'tests',
  workers: 1,
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  reporter: 'list',
  use: {
    // A Swedish reader and editor, as in the demo: the editor view answers in the browser's
    // language, and the tests look for its Swedish texts ("Godkänn", "Röd").
    locale: 'sv-SE',
    trace: 'retain-on-failure',
  },
  projects: [{name: 'desktop', use: {...devices['Desktop Chrome']}}],
});
