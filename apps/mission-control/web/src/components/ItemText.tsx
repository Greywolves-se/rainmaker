import type {ReactNode} from 'react';

/** A title with a small grey line under it — the most common list entry in the mockup. */
export function ItemText({title, meta}: {title: ReactNode; meta: ReactNode}) {
  return (
    <div className="item">
      <span className="item__title">{title}</span>
      <span className="item__meta">{meta}</span>
    </div>
  );
}
