package se.rainmaker.trackingclick.click;

/**
 * The result of checking a click link's token (contract 7.5): the three cases the redirect answers
 * differently (302, 400, 410).
 */
sealed interface ClickLinkCheck {

  /**
   * Signed by us and not expired.
   *
   * @param link what the link says
   */
  record Valid(ClickLink link) implements ClickLinkCheck {}

  /** Not signed by us, changed, or garbled: nothing in it can be trusted. */
  record Invalid() implements ClickLinkCheck {}

  /** Signed by us, but older than its lifetime. */
  record Expired() implements ClickLinkCheck {}
}
