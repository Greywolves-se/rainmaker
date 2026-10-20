/// <reference types="node" />
import {readFileSync} from 'node:fs';
import type {Page} from '@playwright/test';

/** The collector's files, at the same addresses as nginx serves them (#99). */
const COLLECTOR_FILE =
  /^\/(?:projects\.json|[a-z][a-z0-9-]*\/(?:snapshot\.json|start-here\.md))$/;

const MISSION_CONTROL = new URL('../../', import.meta.url);

const sample = (file: string) => readFileSync(new URL(file, MISSION_CONTROL));

/** The shared sample files. Every project gets the same snapshot and report. */
const PROJECTS = sample('contract/fixtures/projects.sample.json');
const SNAPSHOT = sample('contract/fixtures/snapshot.sample.json');
const REPORT = sample('collector/src/test/resources/start-here/sample.md');

/**
 * Answers the app's requests for the collector's output with the sample files, so the built app
 * runs without the collector. Everything else (the app itself) comes from `vite preview`.
 */
export async function serveSampleData(page: Page): Promise<void> {
  await page.route(
    url => COLLECTOR_FILE.test(url.pathname),
    route => {
      const {pathname} = new URL(route.request().url());
      if (pathname.endsWith('/start-here.md')) {
        return route.fulfill({
          body: REPORT,
          contentType: 'text/markdown; charset=utf-8',
        });
      }
      return route.fulfill({
        body: pathname === '/projects.json' ? PROJECTS : SNAPSHOT,
        contentType: 'application/json',
      });
    }
  );
}
