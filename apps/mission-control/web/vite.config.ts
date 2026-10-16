/// <reference types="node" />
import react from '@vitejs/plugin-react';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import type {Plugin} from 'vite';
import {defineConfig} from 'vitest/config';

/**
 * The collector's output (apps/mission-control/out/, git-ignored, #99): projects.json and, per
 * project, <id>/snapshot.json and <id>/start-here.md. The id pattern is the contract's, so a
 * request can never reach outside out/.
 */
const COLLECTOR_FILE =
  /^\/(projects\.json|[a-z][a-z0-9-]*\/(?:snapshot\.json|start-here\.md))$/;
const OUT_FOLDER = new URL('../out/', import.meta.url);

/**
 * Local dev only: serves the collector's output at the same addresses as nginx does in Docker.
 * Answers 404 when the collector has not run, so the app falls back to demo data.
 * `apply: 'serve'` keeps it out of the production build: the files are never bundled.
 */
function serveCollectorFiles(): Plugin {
  return {
    name: 'serve-collector-files',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const match = COLLECTOR_FILE.exec(request.url?.split('?')[0] ?? '');
        if (!match) {
          next();
          return;
        }
        const file = fileURLToPath(new URL(match[1]!, OUT_FOLDER));
        readFile(file).then(
          body => {
            response.setHeader(
              'Content-Type',
              file.endsWith('.md')
                ? 'text/markdown; charset=utf-8'
                : 'application/json'
            );
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
