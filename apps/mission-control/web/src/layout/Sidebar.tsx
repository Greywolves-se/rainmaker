import {useTranslation} from 'react-i18next';
import {NavLink} from 'react-router';
import {LogoIcon} from '../components/icons';
import type {Snapshot} from '../data/types';
import {PAGES} from './pages';

interface SidebarProps {
  projects: Snapshot['projects'];
  sourceSummary: Snapshot['sourceSummary'];
}

export function Sidebar({projects, sourceSummary}: SidebarProps) {
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

      <ul className="nav-list">
        {PAGES.map(({key, path, Icon}) => (
          <li key={key}>
            <NavLink
              to={path}
              end
              className={({isActive}) =>
                isActive ? 'nav-link nav-link--active' : 'nav-link'
              }
            >
              <Icon />
              {t(`nav.${key}`)}
            </NavLink>
          </li>
        ))}
      </ul>

      <div className="project-list">
        <span className="project-list__heading">{t('nav.projects')}</span>
        {projects.map(project => (
          <div key={project.id} className="project-list__item">
            <span
              className={`dot dot--small dot--${project.health}`}
              aria-hidden="true"
            />
            {project.name}
            <span className="sr-only">{t(`health.${project.health}`)}</span>
          </div>
        ))}
      </div>

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
    </nav>
  );
}
