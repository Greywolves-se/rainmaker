/** Written by the collector next to the project's snapshot (#25, #99). */
export function startHereUrl(projectId: string): string {
  return `/${projectId}/start-here.md`;
}

/**
 * The report's exact text, or null when it has not been generated yet (404). Other failures
 * throw, so they show as an error instead of looking like a missing report.
 */
export async function fetchStartHere(
  projectId: string,
  fetchImpl: typeof fetch = fetch
): Promise<string | null> {
  const url = startHereUrl(projectId);
  const response = await fetchImpl(url, {cache: 'no-store'});
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`${url} returned HTTP ${response.status}`);
  }
  return response.text();
}
