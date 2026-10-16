/** Written by the collector next to snapshot.json (#25) and served at the same place. */
export const START_HERE_URL = '/start-here.md';

/**
 * The report's exact text, or null when it has not been generated yet (404). Other failures
 * throw, so they show as an error instead of looking like a missing report.
 */
export async function fetchStartHere(
  fetchImpl: typeof fetch = fetch
): Promise<string | null> {
  const response = await fetchImpl(START_HERE_URL, {cache: 'no-store'});
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`${START_HERE_URL} returned HTTP ${response.status}`);
  }
  return response.text();
}
