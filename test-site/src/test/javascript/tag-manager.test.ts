/**
 * Runs static/js/tag-manager.js against a fake browser (window, document,
 * fetch) to check when it loads which tags. Especially: consent-gated tags
 * must wait until the reader accepts, and a broken container must not throw.
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { runInNewContext } from 'node:vm';
import { createTcfApi } from '../../main/resources/static/js/consent-core.js';

const source = readFileSync(
  join(import.meta.dirname, '..', '..', 'main', 'resources', 'static', 'js', 'tag-manager.js'),
  'utf8',
);

interface Tag {
  name: string;
  src: string;
  trigger: 'pageview' | 'consent';
}

/** Lets pending promise callbacks (fetch -> json -> run) finish. */
async function settle(): Promise<void> {
  for (let round = 0; round < 5; round += 1) {
    await new Promise((resolve) => setImmediate(resolve));
  }
}

function runTagManager(options: {
  tags?: Tag[];
  fetchFails?: boolean;
  tcfApi?: ReturnType<typeof createTcfApi> | null;
}) {
  const injected: string[] = [];
  const warnings: unknown[] = [];
  const window: any = {
    __tcfapi: options.tcfApi === null ? undefined : options.tcfApi?.handle,
  };
  const document = {
    currentScript: { getAttribute: () => '/tag-manager/container.json' },
    createElement: () => {
      const attributes: Record<string, string> = {};
      return {
        async: false,
        src: '',
        setAttribute: (name: string, value: string) => {
          attributes[name] = value;
        },
        attributes,
      };
    },
    head: {
      appendChild: (element: { attributes: Record<string, string> }) => {
        injected.push(element.attributes['data-tag-name'] ?? '?');
      },
    },
  };
  const fetch = () =>
    options.fetchFails === true
      ? Promise.reject(new Error('network down'))
      : Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ containerId: 'TEST', tags: options.tags ?? [] }),
        });
  runInNewContext(source, {
    window,
    document,
    fetch,
    console: { warn: (...args: unknown[]) => warnings.push(args) },
    Date,
  });
  return { window, injected, warnings };
}

const statsTag: Tag = { name: 'vintra-stats', src: '/static/js/vintra-stats.js', trigger: 'consent' };
const widgetTag: Tag = { name: 'rainmaker', src: 'https://cdn.example/r.js', trigger: 'pageview' };

describe('tag manager', () => {
  it('loads page-view tags right away', async () => {
    const { injected } = runTagManager({ tags: [widgetTag], tcfApi: createTcfApi(null) });
    await settle();
    assert.deepEqual(injected, ['rainmaker']);
  });

  it('holds consent tags until the reader accepts all', async () => {
    const tcfApi = createTcfApi(null);
    const { injected } = runTagManager({ tags: [statsTag], tcfApi });
    await settle();
    assert.deepEqual(injected, []);

    tcfApi.setChoice('all');
    assert.deepEqual(injected, ['vintra-stats']);
  });

  it('never loads consent tags for "necessary only"', async () => {
    const tcfApi = createTcfApi(null);
    const { injected } = runTagManager({ tags: [statsTag], tcfApi });
    await settle();
    tcfApi.setChoice('necessary');
    assert.deepEqual(injected, []);
  });

  it('loads consent tags at once when consent was given on an earlier visit', async () => {
    const { injected } = runTagManager({ tags: [statsTag], tcfApi: createTcfApi('all') });
    await settle();
    assert.deepEqual(injected, ['vintra-stats']);
  });

  it('loads a consent tag only once, even if consent is given again', async () => {
    const tcfApi = createTcfApi(null);
    const { injected } = runTagManager({ tags: [statsTag], tcfApi });
    await settle();
    tcfApi.setChoice('all');
    tcfApi.showUi();
    tcfApi.setChoice('all');
    assert.deepEqual(injected, ['vintra-stats']);
  });

  it('never loads consent tags when there is no consent manager', async () => {
    const { injected } = runTagManager({ tags: [statsTag, widgetTag], tcfApi: null });
    await settle();
    assert.deepEqual(injected, ['rainmaker']);
  });

  it('fails quietly when the container cannot be loaded', async () => {
    const { injected, warnings } = runTagManager({ fetchFails: true, tcfApi: createTcfApi(null) });
    await settle();
    assert.deepEqual(injected, []);
    assert.equal(warnings.length, 1);
  });

  it('reports what it did in the dataLayer', async () => {
    const { window } = runTagManager({ tags: [widgetTag], tcfApi: createTcfApi(null) });
    await settle();
    // Array.from copies out of the sandbox, whose arrays have another prototype.
    const events = Array.from(window.dataLayer, (entry: { event: string }) => entry.event);
    assert.deepEqual(events, ['tm.js', 'tm.tag', 'tm.loaded']);
  });
});
