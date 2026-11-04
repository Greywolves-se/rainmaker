package se.rainmaker.trackingclick.api;

import java.util.UUID;

/**
 * Makes the signed click links that the widget gets instead of an advertiser's address (#153,
 * contract 7.5, D-6). Opening one goes through S2, which stores the click once and redirects.
 */
public interface ClickLinks {

  /**
   * A new click link for one approved link, e.g. {@code http://localhost:8081/c/<payload>.<sig>}.
   * Every call makes a new link with a new click id; the same link opened twice is one click.
   *
   * @param articleId the article the link is shown on
   * @param offerId the offer it leads to
   * @param destinationUrl the offer's page; must be a valid absolute http(s) URI
   * @throws IllegalArgumentException if the destination is not a valid absolute http(s) URI. A
   *     caller building several links drops only that one, so one bad offer never costs the panel
   */
  String create(UUID articleId, UUID offerId, String destinationUrl);
}
