import {useTranslation} from 'react-i18next';
import {NavLink} from 'react-router';
import {LogoIcon} from '../components/icons';
import type {ProjectEntry} from '../contract/projects';
import type {Snapshot} from '../data/types';
import {PAGES, projectPath, type PageDefinition} from './pages';

interface SidebarProps {
  projects: ProjectEntry[];
  /** null on the "project not found" page: then there are no page links. */
  currentProject: ProjectEntry | null;
  page: PageDefinition;
  /** null while the project's snapshot loads or failed. */
  sourceSummary: Snapshot['sourceSummary'] | null;
}

const linkClass = ({isActive}: {isActive: boolean}) =>
  isActive ? 'nav-link nav-link--active' : 'nav-link';

export function Sidebar({
  projects,
  currentProject,
  page,
  sourceSummary,
}: SidebarProps) {
  const {t} = useTranslation();
  return (
    <nav className="sidebar" aria-label={t('nav.mainMenu')}>
      <div className="brand">
        <LogoIcon />
        <div className="brand__text">
          <span className="brand__name">{t('app.name')}</span>
          <span className="brand__edition">{t('app.edition')}</span>
        </div>
      </div>

      {currentProject && (
        <ul className="nav-list">
          {PAGES.map(item => (
            <li key={item.key}>
              <NavLink
                to={projectPath(currentProject.id, item)}
                end
                className={linkClass}
              >
                <item.Icon />
                {t(`nav.${item.key}`)}
              </NavLink>
            </li>
          ))}
        </ul>
      )}

      <div className="project-list">
        <span className="project-list__heading" id="project-list-heading">
          {t('nav.projects')}
        </span>
        {/* Each project opens the page you are on, so switching keeps your place. */}
        <ul className="nav-list" aria-labelledby="project-list-heading">
          {projects.map(project => (
            <li key={project.id}>
              {/* "true", not "page": the page link already marks the current page. */}
              <NavLink
                to={projectPath(project.id, page)}
                end
                className={linkClass}
                aria-current="true"
              >
                {project.name}
              </NavLink>
            </li>
          ))}
        </ul>
      </div>

      {sourceSummary && (
        <div className="sync-box">
          <span className="sync-box__label">{t('nav.dataSources')}</span>
          <span>
            <strong>
              {t('nav.synced', {
                synced: sourceSummary.synced,
                total: sourceSummary.total,
              })}
            </strong>
          </span>
          <span className="sync-box__label">{sourceSummary.note}</span>
        </div>
      )}
    </nav>
  );
}
