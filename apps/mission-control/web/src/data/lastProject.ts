/** Key in the browser's localStorage that remembers the last viewed project. */
export const LAST_PROJECT_STORAGE_KEY = 'missionControl.project';

/** The last viewed project id, or null when nothing is saved or storage is blocked. */
export function readLastProject(): string | null {
  try {
    return localStorage.getItem(LAST_PROJECT_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function saveLastProject(projectId: string): void {
  try {
    localStorage.setItem(LAST_PROJECT_STORAGE_KEY, projectId);
  } catch {
    // Private mode or blocked storage: / then opens the first project.
  }
}
