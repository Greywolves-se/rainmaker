import type {ComponentType} from 'react';
import {
  CodeIcon,
  DatabaseIcon,
  DocumentIcon,
  OverviewIcon,
} from '../components/icons';

export type PageKey = 'overview' | 'development' | 'sources' | 'startHere';

export interface PageDefinition {
  key: PageKey;
  /** The URL part after the project: /<project>/<segment>. */
  segment: string;
  Icon: ComponentType;
}

/** Menu order. Titles and subtitles come from `pages.<key>` and `nav.<key>` in the translation files. */
export const PAGES: readonly PageDefinition[] = [
  {key: 'overview', segment: 'overview', Icon: OverviewIcon},
  {key: 'development', segment: 'development', Icon: CodeIcon},
  {key: 'sources', segment: 'sources', Icon: DatabaseIcon},
  {key: 'startHere', segment: 'start-here', Icon: DocumentIcon},
];

export const FIRST_PAGE = PAGES[0]!;

/** The page for a path like /rainmaker/development; the Overview for anything else. */
export function pageForPath(pathname: string): PageDefinition {
  const segment = pathname.split('/').filter(Boolean).at(-1);
  return PAGES.find(page => page.segment === segment) ?? FIRST_PAGE;
}

export function projectPath(projectId: string, page: PageDefinition): string {
  return `/${projectId}/${page.segment}`;
}
