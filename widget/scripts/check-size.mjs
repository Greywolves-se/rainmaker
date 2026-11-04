// Fails the build when dist/rainmaker.js is bigger than 15 KB gzipped: the reader-side budget
// in the architecture (acceptance criteria for A1, Reader). The script loads on every article
// page, so it must stay small.
import {readFileSync} from 'node:fs';
import {gzipSync} from 'node:zlib';

const LIMIT_BYTES = 15 * 1024;
const file = new URL('../dist/rainmaker.js', import.meta.url);
const gzipped = gzipSync(readFileSync(file)).length;

console.log(
  `rainmaker.js: ${gzipped} bytes gzipped (limit ${LIMIT_BYTES} bytes)`
);
if (gzipped > LIMIT_BYTES) {
  console.error('rainmaker.js is over its size budget');
  process.exitCode = 1;
}
