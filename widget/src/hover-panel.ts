import {failOpen} from './fail-open';

/** A deliberate hover: the panel opens only after the pointer has stayed this long (#152). */
export const OPEN_DELAY_MS = 300;

/**
 * Time to move the pointer from the headline into the panel. Without it, the panel would close
 * on the way, and its links couldn't be clicked.
 */
export const CLOSE_DELAY_MS = 200;

type Timer = ReturnType<typeof setTimeout>;

/**
 * Opens a panel below the headline after a deliberate hover, and closes it when the pointer has
 * left both the headline and the panel.
 *
 * @param onHoverStart called at the start of every hover, so the links can load during the delay
 * @param buildPanel builds the panel, or returns null when there is nothing to show
 */
export function attachHoverPanel(
  headline: Element,
  onHoverStart: () => void,
  buildPanel: () => Promise<HTMLElement | null>
): void {
  let panel: HTMLElement | null = null;
  let pointerInside = false;
  let openTimer: Timer | undefined;
  let closeTimer: Timer | undefined;

  const enter = failOpen(() => {
    pointerInside = true;
    clearTimeout(closeTimer);
    if (panel) {
      return;
    }
    onHoverStart();
    clearTimeout(openTimer);
    openTimer = setTimeout(open, OPEN_DELAY_MS);
  });

  const leave = failOpen(() => {
    pointerInside = false;
    clearTimeout(openTimer);
    clearTimeout(closeTimer);
    closeTimer = setTimeout(close, CLOSE_DELAY_MS);
  });

  const open = failOpen(async () => {
    const built = await buildPanel();
    // The pointer may have left, or another hover may have opened a panel, while it loaded.
    if (!built || !pointerInside || panel) {
      return;
    }
    panel = built;
    panel.addEventListener('mouseenter', enter);
    panel.addEventListener('mouseleave', leave);
    document.body.append(panel);
  });

  const close = failOpen(() => {
    if (pointerInside) {
      return;
    }
    panel?.remove();
    panel = null;
  });

  headline.addEventListener('mouseenter', enter);
  headline.addEventListener('mouseleave', leave);
}
