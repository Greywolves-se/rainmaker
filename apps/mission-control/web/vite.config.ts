/// <reference types="node" />
import react from '@vitejs/plugin-react';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import type {Plugin} from 'vite';
import {defineConfig} from 'vitest/config';

/** The collector's output files (apps/mission-control/out/, git-ignored) and their types. */
const COLLECTOR_FILES = {
  '/snapshot.json': 'application/json',
  '/start-here.md': 'text/markdown; charset=utf-8',
};

/**
 * Local dev only: serves the collector's output at /snapshot.json and /start-here.md, like nginx
 * does in Docker. Answers 404 when the collector has not run, so the app falls back to demo data.
 * `apply: 'serve'` keeps it out of the production build: the files are never bundled.
 */
function serveCollectorFiles(): Plugin {
  return {
    name: 'serve-collector-files',
    apply: 'serve',
    configureServer(server) {
      for (const [path, contentType] of Object.entries(COLLECTOR_FILES)) {
        const file = fileURLToPath(new URL(`../out${path}`, import.meta.url));
        server.middlewares.use(path, (_request, response) => {
          readFile(file).then(
            body => {
              response.setHeader('Content-Type', contentType);
              response.setHeader('Cache-Control', 'no-store');
              response.end(body);
            },
            (error: NodeJS.ErrnoException) => {
              // Missing file = the collector has not run; anything else is a real error.
              response.statusCode = error.code === 'ENOENT' ? 404 : 500;
              if (response.statusCode === 500) {
                server.config.logger.error(
                  `Could not read ${file}: ${error.message}`
                );
              }
              response.end();
            }
          );
        });
      }
    },
  };
}

export default defineConfig({
  plugins: [react(), serveCollectorFiles()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
