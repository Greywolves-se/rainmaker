import {useEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {Outlet, useLocation} from 'react-router';
import {useLoadedSnapshot} from '../data/SnapshotProvider';
import {useSnapshot} from '../data/useSnapshot';
import {Navigator} from './Navigator';
import {pageForPath} from './pages';
import {Sidebar} from './Sidebar';
import {SnapshotBanners} from './SnapshotBanners';
import {TopBar} from './TopBar';
import {useNow} from './useNow';

export function Layout() {
  const {t} = useTranslation();
  const snapshot = useSnapshot();
  const loaded = useLoadedSnapshot();
  const now = useNow();
  const page = pageForPath(useLocation().pathname);
  const title = t(`pages.${page.key}.title`);

  useEffect(() => {
    document.title = t('app.documentTitle', {page: title});
  }, [t, title]);

  return (
    <div className="shell">
      <Sidebar
        projects={snapshot.projects}
        sourceSummary={snapshot.sourceSummary}
      />
      <div className="content">
        <TopBar generatedAt={loaded.generatedAt} now={now} />
        <SnapshotBanners loaded={loaded} now={now} />
        <div className="page-header">
          <h1 className="page-header__title">{title}</h1>
          <p className="page-header__subtitle">
            {t(`pages.${page.key}.subtitle`)}
          </p>
        </div>
        <div className="columns">
          <main className="main-column">
            <Outlet />
          </main>
          <Navigator navigator={snapshot.navigator} />
        </div>
      </div>
    </div>
  );
}
