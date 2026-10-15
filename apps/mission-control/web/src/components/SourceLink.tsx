import type {ReactNode} from 'react';

/** True for http(s) addresses only; anything else (e.g. javascript:) is never made a link. */
export function isWebUrl(url: string | null): url is string {
  if (!url) {
    return false;
  }
  try {
    return ['http:', 'https:'].includes(new URL(url).protocol);
  } catch {
    // Not a URL at all: show the text without a link.
    return false;
  }
}

/** Links to the source of an item (GitHub, Drive …) in a new tab, or shows plain text. */
export function SourceLink({
  url,
  children,
}: {
  url: string | null;
  children: ReactNode;
}) {
  if (!isWebUrl(url)) {
    return <>{children}</>;
  }
  return (
    <a href={url} target="_blank" rel="noreferrer">
      {children}
    </a>
  );
}
