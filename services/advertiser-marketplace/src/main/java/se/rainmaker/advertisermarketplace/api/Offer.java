package se.rainmaker.advertisermarketplace.api;

import java.util.UUID;

/**
 * An advertiser's offer (#145, contract 3.4). The same fields go out in {@code GET /api/offers}.
 *
 * @param offerId the offer's id
 * @param advertiser the advertiser's name
 * @param title the link text
 * @param text one sentence under the title
 * @param category a commercial category key (#145, D-3), e.g. {@code "travel"}
 * @param destinationUrl the offer's landing page; never sent to the browser as a field
 * @param cpcOre the price per click in whole öre
 */
public record Offer(
    UUID offerId,
    String advertiser,
    String title,
    String text,
    String category,
    String destinationUrl,
    int cpcOre) {}
