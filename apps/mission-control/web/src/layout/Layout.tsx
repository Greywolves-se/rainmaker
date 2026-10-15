import {useEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {Outlet, useLocation} from 'react-router';
import {useSnapshot} from '../data/useSnapshot';
import {Navigator} from './Navigator';
import {pageForPath} from './pages';
import {Sidebar} from './Sidebar';
import {TopBar} from './TopBar';

export function Layout() {
  const {t} = useTranslation();
  const snapshot = useSnapshot();
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
        <TopBar generatedAt={snapshot.generatedAt} />
        {/* TODO(#18): show only when the demo fallback is used. */}
        <p className="sample-banner" role="note">
          {t('sampleData.banner')}
        </p>
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
