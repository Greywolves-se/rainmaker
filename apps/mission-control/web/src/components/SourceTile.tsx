import type {SourceCode} from '../data/types';

type TileSize = 'small' | 'medium' | 'large';

/** Coloured square with the source's short code (GH, GD …). Decorative: the row text names the source. */
export function SourceTile({code, size}: {code: SourceCode; size: TileSize}) {
  return (
    <span className={`tile tile--${size} tile--${code}`} aria-hidden="true">
      {code}
    </span>
  );
}
