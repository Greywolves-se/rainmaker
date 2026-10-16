import {fireEvent, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {renderApp} from '../../test/renderApp';

/** Exactly what the collector writes, including the trailing newline and a non-ASCII dash. */
const REPORT = '# START HERE – Example project\n\n- **Sprint:** 0 of 2 done\n';

function serve(response: Response) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
}

describe('START HERE page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    // Drops this file's own clipboard stand-ins (jsdom has no clipboard of its own).
    Reflect.deleteProperty(navigator, 'clipboard');
  });

  it('is in the main menu and shows the rendered report', async () => {
    serve(new Response(REPORT));
    renderApp({path: '/start-here'});

    const menu = screen.getByRole('navigation', {name: 'Main menu'});
    expect(
      within(menu).getByRole('link', {name: 'START HERE'})
    ).toHaveAttribute('aria-current', 'page');
    expect(
      await screen.findByRole('heading', {name: 'START HERE – Example project'})
    ).toBeVisible();
    expect(fetch).toHaveBeenCalledWith('/start-here.md', {cache: 'no-store'});
  });

  it('copies the exact file content', async () => {
    serve(new Response(REPORT));
    const user = userEvent.setup();
    const writeText = vi
      .spyOn(navigator.clipboard, 'writeText')
      .mockResolvedValue(undefined);
    renderApp({path: '/start-here'});

    await user.click(await screen.findByRole('button', {name: 'Copy'}));

    expect(writeText).toHaveBeenCalledWith(REPORT);
    expect(await screen.findByRole('status')).toHaveTextContent('Copied');
  });

  it('says so when copying is blocked', async () => {
    serve(new Response(REPORT));
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(
      new Error('NotAllowedError')
    );
    renderApp({path: '/start-here'});

    await user.click(await screen.findByRole('button', {name: 'Copy'}));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Could not copy'
    );
  });

  it('says so when there is no clipboard (plain http)', async () => {
    serve(new Response(REPORT));
    // Outside a secure context browsers have no navigator.clipboard at all.
    Object.defineProperty(navigator, 'clipboard', {
      value: undefined,
      configurable: true,
    });
    renderApp({path: '/start-here'});

    fireEvent.click(await screen.findByRole('button', {name: 'Copy'}));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Could not copy'
    );
  });

  it('downloads the file as start-here.md', async () => {
    serve(new Response(REPORT));
    renderApp({path: '/start-here'});

    const download = await screen.findByRole('link', {name: 'Download .md'});
    expect(download).toHaveAttribute('href', '/start-here.md');
    expect(download).toHaveAttribute('download', 'start-here.md');
  });

  it('explains when the report has not been generated yet', async () => {
    serve(new Response('', {status: 404}));
    renderApp({path: '/start-here'});

    expect(
      await screen.findByText(
        /The START HERE report has not been generated yet/
      )
    ).toBeVisible();
    expect(
      screen.queryByRole('button', {name: 'Copy'})
    ).not.toBeInTheDocument();
  });

  it('shows an error when the report cannot be loaded', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    serve(new Response('', {status: 500}));
    renderApp({path: '/start-here'});

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not load the START HERE report'
    );
  });
});
