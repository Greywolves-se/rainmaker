package se.rainmaker.widgetdelivery.links;

import java.util.List;
import java.util.UUID;

/**
 * The widget API's answer for one article (contract 7.4), as rainmaker.js reads it.
 *
 * @param articleUrl the address rainmaker.js asked about
 * @param links at most three, in rank order; empty when the article may have none
 */
record WidgetLinks(String articleUrl, List<Link> links) {

  /** Copies the list, so the answer cannot change afterwards. */
  WidgetLinks {
    links = List.copyOf(links);
  }

  /**
   * One link in the panel. The offer's own address is never here, only inside the signed click link
   * (contract 7.5).
   *
   * @param clickUrl G1's signed click link
   */
  record Link(UUID offerId, String advertiser, String title, String text, String clickUrl) {}
}
