/// <reference types="node" />
import react from '@vitejs/plugin-react';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import type {Plugin} from 'vite';
import {defineConfig} from 'vitest/config';

/** The collector's output (apps/mission-control/out/), git-ignored. */
const COLLECTOR_SNAPSHOT = fileURLToPath(
  new URL('../out/snapshot.json', import.meta.url)
);

/**
 * Local dev only: serves the collector's snapshot at /snapshot.json, like nginx does in
 * Docker. Answers 404 when the collector has not run, so the app falls back to demo data.
 * `apply: 'serve'` keeps it out of the production build: the snapshot is never bundled.
 */
function serveCollectorSnapshot(): Plugin {
  return {
    name: 'serve-collector-snapshot',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/snapshot.json', (_request, response) => {
        readFile(COLLECTOR_SNAPSHOT).then(
          body => {
            response.setHeader('Content-Type', 'application/json');
            response.setHeader('Cache-Control', 'no-store');
            response.end(body);
          },
          (error: NodeJS.ErrnoException) => {
            // Missing file = the collector has not run; anything else is a real error.
            response.statusCode = error.code === 'ENOENT' ? 404 : 500;
            if (response.statusCode === 500) {
              server.config.logger.error(
                `Could not read ${COLLECTOR_SNAPSHOT}: ${error.message}`
              );
            }
            response.end();
          }
        );
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), serveCollectorSnapshot()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
