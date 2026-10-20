/// <reference types="node" />
import {defineConfig, devices} from '@playwright/test';

const PORT = 4173;

/**
 * End-to-end smoke tests (#29) against the built app: run `npm run build` first. `vite preview`
 * serves dist/, and the tests answer the data requests with the shared sample files
 * (e2e/sampleData.ts). Chromium only, at a desktop and a phone size.
 */
export default defineConfig({
  testDir: 'e2e',
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? [['list'], ['github']] : 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'retain-on-failure',
  },
  projects: [
    {name: 'desktop', use: {...devices['Desktop Chrome']}},
    {name: 'mobile', use: {...devices['Pixel 7']}},
  ],
  webServer: {
    command: `npx vite preview --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env.CI,
  },
});
