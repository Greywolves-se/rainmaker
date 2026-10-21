/**
 * rainmaker.js, the script a publisher adds to its pages with one `<script async>` tag.
 *
 * Fail open (CLAUDE.md, product rules): whatever goes wrong in Rainmaker, the publisher's page
 * must load and work as before. So nothing thrown here ever reaches the page, and nothing is
 * logged to the reader's console either: the page is the publisher's, not ours.
 */
import {start} from './start';

try {
  start();
} catch {
  // Deliberately swallowed: fail open. The widget's own error reporting comes with #152.
}
