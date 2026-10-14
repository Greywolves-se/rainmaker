import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {I18nextProvider} from 'react-i18next';
import {BrowserRouter} from 'react-router';
import {App} from './App';
import {createI18n} from './i18n/i18n';
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
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </I18nextProvider>
  </StrictMode>
);
