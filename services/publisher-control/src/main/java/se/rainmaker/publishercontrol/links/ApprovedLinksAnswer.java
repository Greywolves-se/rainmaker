package se.rainmaker.publishercontrol.links;

import java.util.List;
import java.util.UUID;

/**
 * The answer of {@code GET /api/approved-links} (contract 7.3).
 *
 * @param articleId the article's id, or {@code null} when G2 doesn't know the address
 * @param articleUrl the article's address
 * @param links the approved links, at most three, by rank; empty unless the article is green
 */
record ApprovedLinksAnswer(UUID articleId, String articleUrl, List<Link> links) {

  /**
   * One approved link. {@code destinationUrl} is for G1 to put into the signed click link (contract
   * 7.5); it never goes to the reader's browser as a field.
   */
  record Link(
      UUID offerId,
      int rank,
      String advertiser,
      String title,
      String text,
      String destinationUrl) {}
}
