import {useEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {Outlet, useLocation, useParams} from 'react-router';
import type {ProjectEntry} from '../contract/projects';
import {
  SnapshotDataProvider,
  useLoadedSnapshot,
  useProjects,
} from '../data/SnapshotProvider';
import {useDataSource, useLoad} from '../data/dataSource';
import {saveLastProject} from '../data/lastProject';
import {Navigator} from './Navigator';
import {pageForPath} from './pages';
import {Sidebar} from './Sidebar';
import {SnapshotBanners} from './SnapshotBanners';
import {TopBar} from './TopBar';
import {useNow} from './useNow';

/** /<project>/…: that project's pages, or "project not found" for an unknown id. */
export function ProjectLayout() {
  const {projectId = ''} = useParams();
  const project = useProjects().find(p => p.id === projectId);
  return project ? (
    // A new key per project: switching starts a fresh load instead of showing the old data.
    <ProjectView key={project.id} project={project} />
  ) : (
    <ProjectNotFound projectId={projectId} />
  );
}

function ProjectView({project}: {project: ProjectEntry}) {
  const {t} = useTranslation();
  const source = useDataSource();
  const projects = useProjects();
  const page = pageForPath(useLocation().pathname);
  const state = useLoad(
    () => source.snapshot(project.id),
    source.preloaded?.snapshot?.(project.id),
    `the snapshot of ${project.id}`
  );
  usePageTitle(t(`pages.${page.key}.title`));

  useEffect(() => saveLastProject(project.id), [project.id]);

  return (
    <div className="shell">
      <Sidebar
        projects={projects}
        currentProject={project}
        page={page}
        sourceSummary={
          state.status === 'ready' ? state.value.page.sourceSummary : null
        }
      />
      <div className="content">
        {state.status === 'loading' && (
          <p className="app-status" role="status">
            {t('snapshot.loading')}
          </p>
        )}
        {state.status === 'error' && (
          <div className="app-status app-status--error" role="alert">
            <h1>{t('snapshot.errorTitle')}</h1>
            <p>{t('snapshot.errorHelp')}</p>
            <details>
              <summary>{t('snapshot.errorDetails')}</summary>
              <pre>{state.message}</pre>
            </details>
          </div>
        )}
        {state.status === 'ready' && (
          <SnapshotDataProvider loaded={state.value} project={project}>
            <ProjectContent project={project} />
          </SnapshotDataProvider>
        )}
      </div>
    </div>
  );
}

function ProjectContent({project}: {project: ProjectEntry}) {
  const {t} = useTranslation();
  const loaded = useLoadedSnapshot();
  const now = useNow();
  const page = pageForPath(useLocation().pathname);
  return (
    <>
      <TopBar generatedAt={loaded.generatedAt} now={now} />
      <SnapshotBanners loaded={loaded} now={now} />
      <div className="page-header">
        <h1 className="page-header__title">{t(`pages.${page.key}.title`)}</h1>
        <p className="page-header__subtitle">
          {t(`pages.${page.key}.subtitle`)}
        </p>
      </div>
      <div className="columns">
        <main className="main-column">
          <Outlet />
        </main>
        <Navigator
          navigator={loaded.page.navigator}
          projectName={project.name}
        />
      </div>
    </>
  );
}

function ProjectNotFound({projectId}: {projectId: string}) {
  const {t} = useTranslation();
  const projects = useProjects();
  const page = pageForPath(useLocation().pathname);
  usePageTitle(t('projects.notFoundTitle'));
  return (
    <div className="shell">
      <Sidebar
        projects={projects}
        currentProject={null}
        page={page}
        sourceSummary={null}
      />
      <div className="content">
        <div className="page-header">
          <h1 className="page-header__title">{t('projects.notFoundTitle')}</h1>
          <p className="page-header__subtitle">
            {t('projects.notFoundHelp', {id: projectId})}
          </p>
        </div>
      </div>
    </div>
  );
}

function usePageTitle(title: string) {
  const {t} = useTranslation();
  useEffect(() => {
    document.title = t('app.documentTitle', {page: title});
  }, [t, title]);
}
