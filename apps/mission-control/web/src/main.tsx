import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {I18nextProvider} from 'react-i18next';
import {BrowserRouter} from 'react-router';
import {App} from './App';
import {DataSourceProvider, fileDataSource} from './data/dataSource';
import {createI18n} from './i18n/i18n';
import './styles/theme.css';
import './styles/layout.css';
import './styles/components.css';
import './styles/pages.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Mission Control: #root element missing from index.html');
}

/** Demo data only in local dev, when the collector has not written its files yet. */
const source = fileDataSource(import.meta.env.DEV);

createRoot(container).render(
  <StrictMode>
    <I18nextProvider i18n={createI18n()}>
      <BrowserRouter>
        <DataSourceProvider source={source}>
          <App />
        </DataSourceProvider>
      </BrowserRouter>
    </I18nextProvider>
  </StrictMode>
);
