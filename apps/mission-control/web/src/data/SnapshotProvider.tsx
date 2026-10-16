import {createContext, useContext, type ReactNode} from 'react';
import type {ProjectEntry, ProjectList} from '../contract/projects';
import type {LoadedSnapshot} from './snapshotClient';

const SnapshotContext = createContext<LoadedSnapshot | null>(null);
const ProjectsContext = createContext<ProjectList | null>(null);
const CurrentProjectContext = createContext<ProjectEntry | null>(null);

/** Provides the selected project's loaded snapshot to the pages. */
export function SnapshotDataProvider({
  loaded,
  project,
  children,
}: {
  loaded: LoadedSnapshot;
  project: ProjectEntry;
  children: ReactNode;
}) {
  return (
    <CurrentProjectContext.Provider value={project}>
      <SnapshotContext.Provider value={loaded}>
        {children}
      </SnapshotContext.Provider>
    </CurrentProjectContext.Provider>
  );
}

export function useLoadedSnapshot(): LoadedSnapshot {
  const loaded = useContext(SnapshotContext);
  if (!loaded) {
    throw new Error(
      'useLoadedSnapshot() must be used inside a SnapshotDataProvider'
    );
  }
  return loaded;
}

/** The project whose pages are shown. */
export function useCurrentProject(): ProjectEntry {
  const project = useContext(CurrentProjectContext);
  if (!project) {
    throw new Error(
      'useCurrentProject() must be used inside a SnapshotDataProvider'
    );
  }
  return project;
}

export function ProjectsProvider({
  projects,
  children,
}: {
  projects: ProjectList;
  children: ReactNode;
}) {
  return (
    <ProjectsContext.Provider value={projects}>
      {children}
    </ProjectsContext.Provider>
  );
}

/** The projects from projects.json, in its order. */
export function useProjects(): ProjectEntry[] {
  const projects = useContext(ProjectsContext);
  if (!projects) {
    throw new Error('useProjects() must be used inside a ProjectsProvider');
  }
  return projects.projects;
}
