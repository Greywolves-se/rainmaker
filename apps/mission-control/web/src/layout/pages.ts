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
  path: string;
  Icon: ComponentType;
}

/** Menu order. Titles and subtitles come from `pages.<key>` and `nav.<key>` in the translation files. */
export const PAGES: readonly PageDefinition[] = [
  {key: 'overview', path: '/', Icon: OverviewIcon},
  {key: 'development', path: '/development', Icon: CodeIcon},
  {key: 'sources', path: '/sources', Icon: DatabaseIcon},
  {key: 'startHere', path: '/start-here', Icon: DocumentIcon},
];

export function pageForPath(pathname: string): PageDefinition {
  return PAGES.find(page => page.path === pathname) ?? PAGES[0]!;
}
