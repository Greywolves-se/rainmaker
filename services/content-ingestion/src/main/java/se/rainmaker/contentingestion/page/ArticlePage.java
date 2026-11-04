package se.rainmaker.contentingestion.page;

/**
 * What ingestion reads from an article page.
 *
 * @param text the body text, paragraphs separated by a blank line; never logged
 * @param section the site's section id, e.g. {@code bostad}
 */
public record ArticlePage(String text, String section) {

  /** Leaves out the text, so it can never end up in a log line. */
  @Override
  public String toString() {
    return "ArticlePage[section=" + section + "]";
  }
}
