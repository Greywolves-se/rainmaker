/**
 * The logic of the fake consent manager (CMP), without any DOM code, so it can
 * be unit tested in Node (see src/test/javascript/consent.test.ts).
 *
 * It implements the parts of the IAB TCF v2.2 `__tcfapi` interface that a
 * script like rainmaker.js uses:
 *
 *   __tcfapi('ping', 2, callback)
 *   __tcfapi('addEventListener', 2, callback)     -> called now and on changes
 *   __tcfapi('removeEventListener', 2, callback, listenerId)
 *   __tcfapi('getTCData', 2, callback)            -> deprecated in 2.2, kept
 *
 * IMPORTANT - this is NOT a real CMP:
 * - The tcString is a readable fake ("FAKE-TCF.all"), not an encoded string.
 * - There is no vendor list. `vendor.consents` is empty, because Rainmaker's
 *   own vendor id is not decided yet (open question P7 in the architecture).
 *   A script should look at `purpose.consents` on this test site.
 *
 * @typedef {'all' | 'necessary'} ConsentChoice
 * @typedef {'tcloaded' | 'cmpuishown' | 'useractioncomplete'} EventStatus
 */

export const CONSENT_CHOICES = /** @type {const} */ (['all', 'necessary']);

/** TCF v2.2 defines eleven purposes (1 = store/access info on a device, ...). */
export const PURPOSE_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];

export const STORAGE_KEY = 'vintrabladet.consent';

const CMP_ID = 0; // 0 = not a registered CMP
const CMP_VERSION = 1;
const TCF_POLICY_VERSION = 4; // policy version used by TCF v2.2

/**
 * @param {unknown} value
 * @returns {value is ConsentChoice}
 */
export function isConsentChoice(value) {
  return CONSENT_CHOICES.includes(/** @type {ConsentChoice} */ (value));
}

/**
 * Builds the TCData object that callbacks receive.
 *
 * @param {ConsentChoice | null} choice   null = the reader has not chosen yet
 * @param {EventStatus} eventStatus
 * @param {number | undefined} listenerId
 */
export function buildTcData(choice, eventStatus, listenerId) {
  const granted = choice === 'all';
  /** @type {Record<number, boolean>} */
  const purposeConsents = {};
  for (const id of PURPOSE_IDS) {
    purposeConsents[id] = granted;
  }
  return {
    tcString: choice === null ? '' : `FAKE-TCF.${choice}`,
    tcfPolicyVersion: TCF_POLICY_VERSION,
    cmpId: CMP_ID,
    cmpVersion: CMP_VERSION,
    gdprApplies: true,
    eventStatus,
    cmpStatus: 'loaded',
    listenerId,
    isServiceSpecific: true,
    useNonStandardTexts: false,
    publisherCC: 'SE',
    purposeOneTreatment: false,
    purpose: { consents: purposeConsents, legitimateInterests: {} },
    vendor: { consents: {}, legitimateInterests: {} },
    specialFeatureOptins: {},
    publisher: {
      consents: { ...purposeConsents },
      legitimateInterests: {},
      customPurpose: { consents: {}, legitimateInterests: {} },
      restrictions: {},
    },
  };
}

/**
 * Creates the full __tcfapi implementation.
 *
 * @param {ConsentChoice | null} initialChoice  choice saved from an earlier visit
 */
export function createTcfApi(initialChoice) {
  /** @type {ConsentChoice | null} */
  let choice = initialChoice;
  /** @type {EventStatus} */
  let status = initialChoice === null ? 'cmpuishown' : 'tcloaded';
  let nextListenerId = 1;
  /** @type {Map<number, Function>} */
  const listeners = new Map();

  /**
   * The function installed as window.__tcfapi.
   *
   * @param {string} command
   * @param {number | undefined | null} version
   * @param {Function} callback
   * @param {unknown} [parameter]
   */
  function handle(command, version, callback, parameter) {
    if (typeof callback !== 'function') {
      return;
    }
    // Only version 2 exists; a missing version means "the latest".
    if (version !== 2 && version !== undefined && version !== null) {
      callback(null, false);
      return;
    }
    switch (command) {
      case 'ping':
        callback(
          {
            gdprApplies: true,
            cmpLoaded: true,
            cmpStatus: 'loaded',
            displayStatus: status === 'cmpuishown' ? 'visible' : 'hidden',
            apiVersion: '2.2',
            cmpVersion: CMP_VERSION,
            cmpId: CMP_ID,
            gvlVersion: 0,
            tcfPolicyVersion: TCF_POLICY_VERSION,
          },
          true,
        );
        return;
      case 'getTCData':
        callback(buildTcData(choice, status, undefined), true);
        return;
      case 'addEventListener': {
        const listenerId = nextListenerId;
        nextListenerId += 1;
        listeners.set(listenerId, callback);
        // The spec says: call the listener right away with the current state.
        callback(buildTcData(choice, status, listenerId), true);
        return;
      }
      case 'removeEventListener': {
        const removed = listeners.delete(Number(parameter));
        callback(removed);
        return;
      }
      default:
        callback(null, false);
    }
  }

  /**
   * Called when the reader clicks a button in the banner.
   *
   * @param {ConsentChoice} newChoice
   */
  function setChoice(newChoice) {
    if (!isConsentChoice(newChoice)) {
      throw new Error(`Unknown consent choice: ${String(newChoice)}`);
    }
    choice = newChoice;
    status = 'useractioncomplete';
    for (const [listenerId, listener] of listeners) {
      listener(buildTcData(choice, status, listenerId), true);
    }
  }

  /** Called when the reader reopens the banner via "Cookieinställningar". */
  function showUi() {
    status = 'cmpuishown';
    for (const [listenerId, listener] of listeners) {
      listener(buildTcData(choice, status, listenerId), true);
    }
  }

  return {
    handle,
    setChoice,
    showUi,
    /** @returns {ConsentChoice | null} */
    getChoice: () => choice,
  };
}

/**
 * Installs the full API as window.__tcfapi and answers every call that the
 * stub (tcf-stub.js) queued before this code had loaded.
 *
 * @param {{ __tcfapi?: Function & { queue?: unknown[][] } }} target  usually window
 * @param {ReturnType<typeof createTcfApi>} api
 */
export function takeOverFromStub(target, api) {
  const queued = typeof target.__tcfapi === 'function' ? target.__tcfapi.queue ?? [] : [];
  target.__tcfapi = api.handle;
  for (const [command, version, callback, parameter] of queued) {
    api.handle(
      /** @type {string} */ (command),
      /** @type {number} */ (version),
      /** @type {Function} */ (callback),
      parameter,
    );
  }
}

/**
 * Reads a saved choice. Storage can be missing or throw (private browsing,
 * blocked cookies), which simply means "no choice yet".
 *
 * @param {Pick<Storage, 'getItem'> | null | undefined} storage
 * @returns {ConsentChoice | null}
 */
export function readStoredChoice(storage) {
  try {
    const value = storage?.getItem(STORAGE_KEY);
    return isConsentChoice(value) ? value : null;
  } catch {
    return null;
  }
}

/**
 * @param {Pick<Storage, 'setItem'> | null | undefined} storage
 * @param {ConsentChoice} choice
 */
export function storeChoice(storage, choice) {
  try {
    storage?.setItem(STORAGE_KEY, choice);
  } catch {
    // Not being able to remember the choice is fine - the banner shows again.
  }
}
