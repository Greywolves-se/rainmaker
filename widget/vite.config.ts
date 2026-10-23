import {defineConfig} from 'vitest/config';

/**
 * Builds one self-contained script, dist/rainmaker.js: an immediately invoked function, so it
 * adds nothing to the publisher's global scope and can be loaded with a plain
 * `<script async src=".../rainmaker.js">`. No chunks, no CSS file, no framework.
 */
export default defineConfig({
  build: {
    target: 'es2020',
    lib: {
      entry: 'src/main.ts',
      formats: ['iife'],
      // Required by Vite for IIFE builds. main.ts exports nothing, so no global is created.
      name: 'Rainmaker',
      fileName: () => 'rainmaker.js',
    },
  },
  test: {
    environment: 'jsdom',
  },
});
