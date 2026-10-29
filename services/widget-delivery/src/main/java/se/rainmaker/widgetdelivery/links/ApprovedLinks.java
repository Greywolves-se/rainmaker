package se.rainmaker.widgetdelivery.links;

import java.util.List;
import java.util.UUID;

/**
 * G3's answer for one article (contract 7.3): only the links G3 may give now, in G3's order.
 *
 * @param articleId Rainmaker's id for the article, or {@code null} when G3 doesn't know it
 * @param links the approved links; empty for an unknown, not green or not yet approved article
 */
record ApprovedLinks(UUID articleId, List<ApprovedLink> links) {

  /** Copies the list, so the answer cannot change afterwards. */
  ApprovedLinks {
    links = List.copyOf(links);
  }
}
