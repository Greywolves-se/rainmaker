package se.rainmaker.advertisermarketplace.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The offers, for modules in G3 (e.g. the editor page, #150). Other apps use the REST API. */
public interface OfferCatalog {

  /** Every offer, in a fixed order (by id). */
  List<Offer> all();

  /** The offer with this id, if any. */
  Optional<Offer> find(UUID offerId);
}
