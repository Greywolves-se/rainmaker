package se.rainmaker.advertisermarketplace.offer;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se.rainmaker.advertisermarketplace.api.Offer;
import se.rainmaker.advertisermarketplace.api.OfferCatalog;

/** {@code GET /api/offers}: every offer, for matching in G2 (#145, contract 7.2). */
@RestController
class OffersController {

  private final OfferCatalog offers;

  OffersController(OfferCatalog offers) {
    this.offers = offers;
  }

  /** The answer's shape: {@code {"offers": [...]}}. */
  record OfferList(List<Offer> offers) {}

  @GetMapping("/api/offers")
  OfferList offers() {
    return new OfferList(offers.all());
  }
}
