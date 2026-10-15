import {render} from '@testing-library/react';
import {I18nextProvider} from 'react-i18next';
import {MemoryRouter} from 'react-router-dom';
import {App} from '../App';
import {createI18n} from '../i18n/i18n';
import {ROUTER_FUTURE} from '../routerFuture';

/** Renders the whole app at `path` with its own i18n instance, so tests never share state. */
export function renderApp({path = '/'}: {path?: string} = {}) {
  return render(
    <I18nextProvider i18n={createI18n('en')}>
      <MemoryRouter initialEntries={[path]} future={ROUTER_FUTURE}>
        <App />
      </MemoryRouter>
    </I18nextProvider>
  );
}
