/**
 * rainmaker.js, the script a publisher adds to its pages with one `<script async>` tag.
 *
 * Fail open (CLAUDE.md, product rules): whatever goes wrong in Rainmaker, the publisher's page
 * must load and work as before. So nothing thrown here ever reaches the page, and the widget logs
 * nothing to the reader's console either: the page is the publisher's, not ours. (The browser
 * itself still logs a request that fails, e.g. when the API is down; a script can't stop that.)
 *
 * This file only covers what runs while the script loads. The hover handlers and the API call
 * run later and catch their own errors (fail-open.ts).
 *
 * Export nothing from here: with the IIFE build, an export becomes a global variable on the
 * publisher's page.
 */
import {ignoreError} from './fail-open';
import {start} from './start';

try {
  // Only set while this script runs for the first time, so it is read here, not later.
  start(document.currentScript);
} catch (error) {
  ignoreError(error);
}
