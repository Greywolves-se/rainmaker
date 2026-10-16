// Renders what the collector really writes (#25): its golden report and the static intro.
// Excluded from `tsc` like the other *.contract.test files: the Docker build copies only web/.
import {render, screen} from '@testing-library/react';
import {describe, expect, it} from 'vitest';
import golden from '../../../../collector/src/test/resources/start-here/sample.md?raw';
import intro from '../../../../data/start-here-intro.md?raw';
import {Markdown} from './markdown';

describe.each([
  ['the collector golden report', golden],
  ['the START HERE intro', intro],
])('Markdown with %s', (_name, source) => {
  it('leaves no Markdown syntax behind as text', () => {
    const {container} = render(<Markdown source={source} />);
    const text = container.textContent ?? '';

    expect(text).not.toMatch(/\*\*|\]\(|^#|`/m);
  });

  it('turns every link into a link', () => {
    render(<Markdown source={source} />);

    const links = source.match(/\[[^\]]+\]\(https?:[^)\s]+\)/g) ?? [];
    expect(screen.queryAllByRole('link')).toHaveLength(links.length);
  });
});

describe('Markdown with the collector golden report', () => {
  it('shows its sections as headings', () => {
    render(<Markdown source={golden} />);

    expect(
      screen.getAllByRole('heading').map(heading => heading.textContent)
    ).toEqual([
      'START HERE – Example project',
      'What we build',
      'Status',
      'Next work',
      'Pull requests waiting for review',
      'Decisions',
      'Risks',
      'Open questions',
      'Latest release',
      'Links',
    ]);
  });
});
