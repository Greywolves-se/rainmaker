import {Navigate, Route, Routes} from 'react-router';
import {ProjectLayout} from './layout/Layout';
import {ProjectsGate, RedirectToProject} from './layout/ProjectsGate';
import {PAGES} from './layout/pages';
import {SourcesPage} from './pages/SourcesPage';
import {DevelopmentPage} from './pages/development/DevelopmentPage';
import {OverviewPage} from './pages/overview/OverviewPage';
import {StartHerePage} from './pages/startHere/StartHerePage';

export function App() {
  return (
    <Routes>
      <Route element={<ProjectsGate />}>
        <Route index element={<RedirectToProject />} />
        {/* The routes before #100 (/development …) open that page in the last viewed project. */}
        {PAGES.map(page => (
          <Route
            key={page.key}
            path={page.segment}
            element={<RedirectToProject page={page} />}
          />
        ))}
        <Route path=":projectId" element={<ProjectLayout />}>
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<OverviewPage />} />
          <Route path="development" element={<DevelopmentPage />} />
          <Route path="sources" element={<SourcesPage />} />
          <Route path="start-here" element={<StartHerePage />} />
          <Route path="*" element={<Navigate to="overview" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}
