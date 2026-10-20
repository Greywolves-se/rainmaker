/**
 * The fake consent manager (CMP). These are the rules rainmaker.js will rely
 * on when it asks for consent through the IAB TCF v2.2 __tcfapi interface.
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { runInNewContext } from 'node:vm';
import {
  PURPOSE_IDS,
  STORAGE_KEY,
  buildTcData,
  createTcfApi,
  readStoredChoice,
  storeChoice,
  takeOverFromStub,
} from '../../main/resources/static/js/consent-core.js';

type Callback = (data: any, success: boolean) => void;

/** Collects everything a callback receives. */
function recorder(): { calls: Array<[any, boolean]>; callback: Callback } {
  const calls: Array<[any, boolean]> = [];
  return { calls, callback: (data, success) => calls.push([data, success]) };
}

describe('TCData', () => {
  it('grants all eleven purposes when the reader accepts all', () => {
    const data = buildTcData('all', 'useractioncomplete', 1);
    assert.equal(data.tcString, 'FAKE-TCF.all');
    assert.equal(data.gdprApplies, true);
    assert.equal(data.tcfPolicyVersion, 4);
    assert.deepEqual(Object.keys(data.purpose.consents).map(Number), PURPOSE_IDS);
    assert.ok(Object.values(data.purpose.consents).every((granted) => granted === true));
  });

  it('grants no purposes for "necessary only"', () => {
    const data = buildTcData('necessary', 'useractioncomplete', 1);
    assert.ok(Object.values(data.purpose.consents).every((granted) => granted === false));
  });

  it('has an empty tcString before the reader has chosen', () => {
    const data = buildTcData(null, 'cmpuishown', undefined);
    assert.equal(data.tcString, '');
    assert.equal(data.purpose.consents[1], false);
  });
});

describe('__tcfapi', () => {
  it('answers ping as a loaded CMP', () => {
    const api = createTcfApi(null);
    const { calls, callback } = recorder();
    api.handle('ping', 2, callback);
    assert.equal(calls[0]?.[0].cmpLoaded, true);
    assert.equal(calls[0]?.[0].apiVersion, '2.2');
    assert.equal(calls[0]?.[0].displayStatus, 'visible');
  });

  it('calls a new listener immediately with the current state', () => {
    const api = createTcfApi(null);
    const { calls, callback } = recorder();
    api.handle('addEventListener', 2, callback);
    assert.equal(calls.length, 1);
    assert.equal(calls[0]?.[0].eventStatus, 'cmpuishown');
    assert.equal(calls[0]?.[0].listenerId, 1);
    assert.equal(calls[0]?.[1], true);
  });

  it('reports "tcloaded" when a choice was saved on an earlier visit', () => {
    const api = createTcfApi('all');
    const { calls, callback } = recorder();
    api.handle('addEventListener', 2, callback);
    assert.equal(calls[0]?.[0].eventStatus, 'tcloaded');
    assert.equal(calls[0]?.[0].purpose.consents[1], true);
  });

  it('notifies listeners when the reader chooses', () => {
    const api = createTcfApi(null);
    const { calls, callback } = recorder();
    api.handle('addEventListener', 2, callback);
    api.setChoice('all');
    assert.equal(calls.length, 2);
    assert.equal(calls[1]?.[0].eventStatus, 'useractioncomplete');
    assert.equal(calls[1]?.[0].purpose.consents[1], true);
  });

  it('notifies listeners when the banner is reopened', () => {
    const api = createTcfApi('all');
    const { calls, callback } = recorder();
    api.handle('addEventListener', 2, callback);
    api.showUi();
    assert.equal(calls[1]?.[0].eventStatus, 'cmpuishown');
  });

  it('stops notifying a removed listener', () => {
    const api = createTcfApi(null);
    const listener = recorder();
    api.handle('addEventListener', 2, listener.callback);
    const removal = recorder();
    api.handle('removeEventListener', 2, removal.callback, 1);
    assert.deepEqual(removal.calls[0], [true, undefined]);
    api.setChoice('necessary');
    assert.equal(listener.calls.length, 1);
  });

  it('answers getTCData', () => {
    const api = createTcfApi('necessary');
    const { calls, callback } = recorder();
    api.handle('getTCData', 2, callback);
    assert.equal(calls[0]?.[0].tcString, 'FAKE-TCF.necessary');
  });

  it('rejects unknown versions and commands', () => {
    const api = createTcfApi(null);
    const { calls, callback } = recorder();
    api.handle('ping', 1, callback);
    api.handle('doSomething', 2, callback);
    assert.deepEqual(calls, [[null, false], [null, false]]);
  });

  it('ignores calls without a callback instead of crashing', () => {
    const api = createTcfApi(null);
    assert.doesNotThrow(() => api.handle('ping', 2, undefined as unknown as Callback));
  });

  it('refuses an unknown choice', () => {
    const api = createTcfApi(null);
    assert.throws(() => api.setChoice('everything' as 'all'), /Unknown consent choice/);
  });
});

describe('saved choice', () => {
  it('reads a valid saved choice', () => {
    assert.equal(readStoredChoice({ getItem: () => 'all' }), 'all');
  });

  it('treats garbage, missing or blocked storage as "no choice"', () => {
    assert.equal(readStoredChoice({ getItem: () => 'yes please' }), null);
    assert.equal(readStoredChoice(null), null);
    assert.equal(
      readStoredChoice({
        getItem: () => {
          throw new Error('blocked');
        },
      }),
      null,
    );
  });

  it('saves under its key and survives blocked storage', () => {
    const saved = new Map<string, string>();
    storeChoice({ setItem: (key, value) => saved.set(key, value) }, 'necessary');
    assert.equal(saved.get(STORAGE_KEY), 'necessary');
    assert.doesNotThrow(() =>
      storeChoice(
        {
          setItem: () => {
            throw new Error('blocked');
          },
        },
        'all',
      ),
    );
  });
});

describe('stub (tcf-stub.js) and hand-over', () => {
  const stubSource = readFileSync(
    join(import.meta.dirname, '..', '..', 'main', 'resources', 'static', 'js', 'tcf-stub.js'),
    'utf8',
  );

  /** Runs tcf-stub.js in a fake browser window, before <body> exists. */
  function runStub(): any {
    const window: any = { frames: {} };
    const document = { body: null };
    runInNewContext(stubSource, { window, document, setTimeout: () => 0 });
    return window;
  }

  it('answers ping right away and queues everything else', () => {
    const window = runStub();
    const ping = recorder();
    window.__tcfapi('ping', 2, ping.callback);
    assert.equal(ping.calls[0]?.[0].cmpStatus, 'stub');
    assert.equal(ping.calls[0]?.[0].cmpLoaded, false);

    const early = recorder();
    window.__tcfapi('addEventListener', 2, early.callback);
    assert.equal(early.calls.length, 0);
    assert.equal(window.__tcfapi.queue.length, 1);
  });

  it('hands queued calls over to the real API', () => {
    const window = runStub();
    const early = recorder();
    window.__tcfapi('addEventListener', 2, early.callback);

    const api = createTcfApi('all');
    takeOverFromStub(window, api);

    assert.equal(window.__tcfapi, api.handle);
    assert.equal(early.calls.length, 1);
    assert.equal(early.calls[0]?.[0].eventStatus, 'tcloaded');
  });

  it('does not replace a CMP that is already installed', () => {
    const existing = () => 'real cmp';
    const window: any = { frames: {}, __tcfapi: existing };
    runInNewContext(stubSource, { window, document: { body: null }, setTimeout: () => 0 });
    assert.equal(window.__tcfapi, existing);
  });
});
