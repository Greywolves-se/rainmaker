import {useTranslation} from 'react-i18next';
import {Navigate, Outlet} from 'react-router';
import {ProjectsProvider, useProjects} from '../data/SnapshotProvider';
import {useDataSource, useLoad} from '../data/dataSource';
import {readLastProject} from '../data/lastProject';
import {FIRST_PAGE, projectPath, type PageDefinition} from './pages';

/** Loads projects.json once; until then, or when it fails, shows a full-page status. */
export function ProjectsGate() {
  const {t} = useTranslation();
  const source = useDataSource();
  const state = useLoad(
    () => source.projects(),
    source.preloaded?.projects,
    'the project list'
  );

  if (state.status === 'loading') {
    return (
      <p className="app-status" role="status">
        {t('projects.loading')}
      </p>
    );
  }
  if (state.status === 'error') {
    return (
      <div className="app-status app-status--error" role="alert">
        <h1>{t('projects.errorTitle')}</h1>
        <p>{t('projects.errorHelp')}</p>
        <details>
          <summary>{t('snapshot.errorDetails')}</summary>
          <pre>{state.message}</pre>
        </details>
      </div>
    );
  }
  return (
    <ProjectsProvider projects={state.value}>
      <Outlet />
    </ProjectsProvider>
  );
}

/** "/" and the old routes (/development …): the last viewed project if it still exists, else the first. */
export function RedirectToProject({
  page = FIRST_PAGE,
}: {
  page?: PageDefinition;
}) {
  const projects = useProjects();
  const last = readLastProject();
  const project = projects.find(p => p.id === last) ?? projects[0]!;
  return <Navigate to={projectPath(project.id, page)} replace />;
}
