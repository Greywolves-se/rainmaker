import {Navigate, Route, Routes} from 'react-router';
import {Layout} from './layout/Layout';
import {OverviewPage} from './pages/overview/OverviewPage';
import {SourcesPage} from './pages/SourcesPage';
import {DevelopmentPage} from './pages/development/DevelopmentPage';
import {StartHerePage} from './pages/startHere/StartHerePage';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<OverviewPage />} />
        <Route path="development" element={<DevelopmentPage />} />
        <Route path="sources" element={<SourcesPage />} />
        <Route path="start-here" element={<StartHerePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
