package se.rainmaker.trackingclick.click;

import java.time.Instant;
import java.util.UUID;

/**
 * What a valid click link says (contract 7.5).
 *
 * @param clickId the click's id; the same link opened twice has the same id
 * @param articleId the article the link was shown on
 * @param offerId the offer it leads to
 * @param destinationUrl where the reader is sent; only ever taken from a signed link
 * @param expiresAt when the link stops working
 */
record ClickLink(
    UUID clickId, UUID articleId, UUID offerId, String destinationUrl, Instant expiresAt) {}
