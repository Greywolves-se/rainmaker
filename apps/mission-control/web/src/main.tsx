import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {I18nextProvider} from 'react-i18next';
import {BrowserRouter} from 'react-router-dom';
import {App} from './App';
import {createI18n} from './i18n/i18n';
import {ROUTER_FUTURE} from './routerFuture';
import './styles/theme.css';
import './styles/layout.css';
import './styles/components.css';
import './styles/pages.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Mission Control: #root element missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <I18nextProvider i18n={createI18n()}>
      <BrowserRouter future={ROUTER_FUTURE}>
        <App />
      </BrowserRouter>
    </I18nextProvider>
  </StrictMode>
);
