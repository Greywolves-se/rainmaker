import {createElement, type ReactNode} from 'react';
import {SourceLink} from '../../components/SourceLink';

/**
 * Renders the small Markdown subset the START HERE report uses (#25): headings, paragraphs,
 * "- " lists, "> " quotes, **bold**, _italic_, `code` and [links](url). Everything becomes React
 * elements, so text is always escaped: raw HTML is shown as text, never run. Links open only for
 * http(s) addresses (SourceLink). Anything else in the file is shown as plain text.
 */
export function Markdown({source}: {source: string}) {
  return <div className="markdown">{toBlocks(source)}</div>;
}

type Block =
  | {kind: 'heading'; level: number; text: string}
  | {kind: 'quote'; text: string}
  | {kind: 'list'; items: string[]}
  | {kind: 'paragraph'; text: string};

function toBlocks(source: string): ReactNode[] {
  const blocks: Block[] = [];
  for (const line of source.replace(/\r\n/g, '\n').split('\n')) {
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    const last = blocks.at(-1);
    if (line.trim() === '') {
      blocks.push({kind: 'paragraph', text: ''}); // ends the current paragraph or list
    } else if (heading) {
      blocks.push({
        kind: 'heading',
        level: heading[1]!.length,
        text: heading[2]!,
      });
    } else if (line.startsWith('> ')) {
      blocks.push({kind: 'quote', text: line.slice(2)});
    } else if (line.startsWith('- ')) {
      if (last?.kind === 'list') {
        last.items.push(line.slice(2));
      } else {
        blocks.push({kind: 'list', items: [line.slice(2)]});
      }
    } else if (last?.kind === 'paragraph' && last.text !== '') {
      last.text += ' ' + line.trim();
    } else {
      blocks.push({kind: 'paragraph', text: line.trim()});
    }
  }
  return blocks
    .filter(block => block.kind !== 'paragraph' || block.text !== '')
    .map((block, index) => renderBlock(block, index));
}

function renderBlock(block: Block, key: number): ReactNode {
  switch (block.kind) {
    case 'heading':
      // The page title is the h1, so the report's headings start at h2.
      return createElement(
        `h${Math.min(block.level + 1, 6)}`,
        {key},
        inline(block.text)
      );
    case 'quote':
      return (
        <blockquote key={key}>
          <p>{inline(block.text)}</p>
        </blockquote>
      );
    case 'list':
      return (
        <ul key={key}>
          {block.items.map((item, index) => (
            <li key={index}>{inline(item)}</li>
          ))}
        </ul>
      );
    case 'paragraph':
      return <p key={key}>{inline(block.text)}</p>;
  }
}

/**
 * Code first, so `click_id` is not read as italic. Italic needs a non-word character on both
 * sides, so snake_case words stay text.
 */
const INLINE =
  /`([^`]+)`|\*\*([^*]+)\*\*|\[([^\]]+)\]\(([^)\s]+)\)|(?<!\w)_([^_]+)_(?!\w)/g;

function inline(text: string): ReactNode[] {
  const parts: ReactNode[] = [];
  let rest = 0;
  for (const match of text.matchAll(INLINE)) {
    const [whole, code, bold, linkText, url, italic] = match;
    parts.push(text.slice(rest, match.index));
    const key = parts.length;
    if (code !== undefined) {
      parts.push(<code key={key}>{code}</code>);
    } else if (bold !== undefined) {
      parts.push(<strong key={key}>{bold}</strong>);
    } else if (linkText !== undefined) {
      parts.push(
        <SourceLink key={key} url={url ?? null}>
          {linkText}
        </SourceLink>
      );
    } else {
      parts.push(<em key={key}>{italic}</em>);
    }
    rest = match.index + whole.length;
  }
  parts.push(text.slice(rest));
  return parts;
}
