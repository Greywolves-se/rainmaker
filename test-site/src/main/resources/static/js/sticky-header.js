/**
 * The section menu sticks to the top of the screen (pure CSS). When the big
 * logo has scrolled out of view, this adds the class `is-stuck`, which shows a
 * small logo inside the menu bar - the way most news sites do it.
 *
 * Sticky headers matter for Rainmaker: the panel must not end up underneath
 * them.
 */
export function initStickyHeader() {
  const brand = document.querySelector('[data-site-brand]');
  const navigation = document.querySelector('[data-section-nav]');
  if (brand === null || navigation === null || !('IntersectionObserver' in window)) {
    return;
  }
  const observer = new IntersectionObserver(([entry]) => {
    navigation.classList.toggle('is-stuck', entry !== undefined && !entry.isIntersecting);
  });
  observer.observe(brand);
}
