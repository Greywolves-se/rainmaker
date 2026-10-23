import {ignoreError} from './fail-open';

/** One approved link, as the panel shows it (contract, section 7.4). */
export interface ApprovedLink {
  advertiser: string;
  title: string;
  text: string;
  /** G1's signed click link. Never the advertiser's own address. */
  clickUrl: string;
}

/** How long the reader may wait for the links before the widget gives up. */
export const API_TIMEOUT_MS = 2000;

const MAX_LINKS = 3;

/**
 * Asks G1's widget API for the article's approved links. Never rejects: an error, a timeout or
 * an answer other than 200 with links all mean no links, so no panel (fail open).
 */
export async function fetchApprovedLinks(
  apiOrigin: string,
  articleUrl: string
): Promise<ApprovedLink[]> {
  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), API_TIMEOUT_MS);
  try {
    const response = await fetch(
      `${apiOrigin}/widget/v1/links?url=${encodeURIComponent(articleUrl)}`,
      {
        signal: timeout.signal,
        credentials: 'omit',
        referrerPolicy: 'no-referrer',
      }
    );
    if (response.status !== 200) {
      return [];
    }
    return parseLinks(await response.json(), apiOrigin);
  } catch (error) {
    ignoreError(error);
    return [];
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Reads the links from the API's answer. Skips a link that lacks a field or whose click link
 * isn't on the widget API, so an advertiser's address never reaches the page directly.
 */
export function parseLinks(body: unknown, apiOrigin: string): ApprovedLink[] {
  if (!isRecord(body) || !Array.isArray(body.links)) {
    return [];
  }
  return body.links
    .map(link => toApprovedLink(link, apiOrigin))
    .filter((link): link is ApprovedLink => link !== null)
    .slice(0, MAX_LINKS);
}

function toApprovedLink(link: unknown, apiOrigin: string): ApprovedLink | null {
  if (!isRecord(link)) {
    return null;
  }
  const {advertiser, title, text, clickUrl} = link;
  if (
    typeof advertiser !== 'string' ||
    typeof title !== 'string' ||
    typeof text !== 'string' ||
    typeof clickUrl !== 'string' ||
    !isOnOrigin(clickUrl, apiOrigin)
  ) {
    return null;
  }
  return {advertiser, title, text, clickUrl};
}

function isOnOrigin(url: string, origin: string): boolean {
  try {
    return new URL(url).origin === origin;
  } catch {
    // Not an absolute URL (URL.canParse is too new for older browsers).
    return false;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
