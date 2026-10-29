package se.rainmaker.widgetdelivery.links;

import java.util.UUID;

/**
 * One approved link from G3 (contract 7.3).
 *
 * @param rank 1 for the best match
 * @param destinationUrl the offer's page. It only goes into the signed click link (contract 7.5),
 *     never to the browser as a field
 */
record ApprovedLink(
    UUID offerId, int rank, String advertiser, String title, String text, String destinationUrl) {}
