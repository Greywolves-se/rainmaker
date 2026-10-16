import {screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {DEMO_SNAPSHOT, type LoadedSnapshot} from './data/snapshotClient';
import {LAST_PROJECT_STORAGE_KEY} from './data/lastProject';
import {renderApp, renderAppWithSource, TEST_PROJECTS} from './test/renderApp';

/** The sample snapshot for both projects, told apart by the sprint name. */
function snapshotFor(projectId: string): LoadedSnapshot {
  const loaded = structuredClone(DEMO_SNAPSHOT);
  loaded.page.overview.sprint = {
    name:
      projectId === 'rainmaker' ? 'Rainmaker sprint' : 'Mission Control sprint',
    done: 1,
    total: 2,
    url: null,
  };
  return loaded;
}

const menu = () => screen.getByRole('navigation', {name: 'Main menu'});
const projectLink = (name: string) => within(menu()).getByRole('link', {name});

describe('projects', () => {
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('lists the projects from projects.json as links, the selected one highlighted', () => {
    renderApp({path: '/mission-control/overview', loaded: snapshotFor});

    expect(projectLink('Rainmaker')).toHaveAttribute(
      'href',
      '/rainmaker/overview'
    );
    expect(projectLink('Mission Control')).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(projectLink('Rainmaker')).not.toHaveAttribute('aria-current');
  });

  it('opens a shared URL straight on that project and page', () => {
    renderApp({path: '/mission-control/development', loaded: snapshotFor});

    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Development'
    );
    expect(projectLink('Mission Control')).toHaveAttribute(
      'href',
      '/mission-control/development'
    );
  });

  it('sends / to the first project when none was viewed before', () => {
    renderApp({loaded: snapshotFor});

    expect(projectLink('Rainmaker')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByText('Rainmaker sprint')).toBeVisible();
  });

  it('sends / and old routes to the last viewed project', () => {
    localStorage.setItem(LAST_PROJECT_STORAGE_KEY, 'mission-control');

    renderApp({path: '/development', loaded: snapshotFor});

    expect(projectLink('Mission Control')).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Development'
    );
  });

  it('ignores a remembered project that no longer exists', () => {
    localStorage.setItem(LAST_PROJECT_STORAGE_KEY, 'gone');

    renderApp({loaded: snapshotFor});

    expect(projectLink('Rainmaker')).toHaveAttribute('aria-current', 'page');
  });

  it('switches project on the same page without a reload, and remembers it', async () => {
    const snapshot = vi.fn((id: string) => Promise.resolve(snapshotFor(id)));
    renderAppWithSource({path: '/rainmaker/overview', snapshot});
    expect(await screen.findByText('Rainmaker sprint')).toBeVisible();

    await userEvent.click(projectLink('Mission Control'));

    expect(await screen.findByText('Mission Control sprint')).toBeVisible();
    expect(screen.queryByText('Rainmaker sprint')).not.toBeInTheDocument();
    expect(snapshot.mock.calls.map(([id]) => id)).toEqual([
      'rainmaker',
      'mission-control',
    ]);
    expect(localStorage.getItem(LAST_PROJECT_STORAGE_KEY)).toBe(
      'mission-control'
    );
  });

  it('keeps the menu while a project loads or fails, so you can switch away', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const snapshot = (id: string) =>
      id === 'rainmaker'
        ? Promise.reject(new Error('HTTP 500'))
        : Promise.resolve(snapshotFor(id));
    renderAppWithSource({path: '/rainmaker/overview', snapshot});

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not load the snapshot'
    );
    await userEvent.click(projectLink('Mission Control'));

    expect(await screen.findByText('Mission Control sprint')).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows "project not found" for an unknown project', () => {
    renderApp({path: '/nope/overview', loaded: snapshotFor});

    expect(screen.getByRole('heading', {level: 1})).toHaveTextContent(
      'Project not found'
    );
    expect(screen.getByText(/There is no project “nope”/)).toBeVisible();
    expect(projectLink('Rainmaker')).toBeVisible();
  });

  it('lets the keyboard reach and open a project', async () => {
    renderApp({path: '/rainmaker/overview', loaded: snapshotFor});

    projectLink('Mission Control').focus();
    await userEvent.keyboard('{Enter}');

    expect(projectLink('Mission Control')).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(screen.getByText('Mission Control sprint')).toBeVisible();
  });

  it('names the selected project in the Navigator panel', () => {
    renderApp({path: '/mission-control/overview', loaded: snapshotFor});

    const navigator = screen.getByRole('complementary', {name: 'Navigator'});
    expect(navigator).toHaveTextContent('Mission Control');
  });

  it('uses the project list it was given, in its order', () => {
    renderApp({
      projects: {
        schemaVersion: 1,
        projects: [...TEST_PROJECTS.projects].reverse(),
      },
      loaded: snapshotFor,
    });

    expect(projectLink('Mission Control')).toHaveAttribute(
      'aria-current',
      'page'
    );
  });
});
