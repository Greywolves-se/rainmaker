package se.rainmaker.advertisermarketplace.offer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.rainmaker.advertisermarketplace.api.Offer;
import se.rainmaker.advertisermarketplace.api.OfferCatalog;

/** The landing page escapes the offer's text and tells caches it varies by language (#149). */
class LandingPageControllerTest {

  private static final UUID OFFER_ID = UUID.fromString("01a1629f-c401-79e3-9779-9e3779b10009");

  /**
   * An offer whose every text field tries to inject HTML. The real offers contain nothing to
   * escape.
   */
  private static final Offer HOSTILE_OFFER =
      new Offer(
          OFFER_ID,
          "<b>Firma</b> & Co",
          "<script>alert('title')</script>",
          "\"><img src=x onerror=alert(1)>",
          "travel",
          "http://localhost:8083/landing/" + OFFER_ID,
          100);

  private final LandingPageController controller =
      new LandingPageController(new SingleOfferCatalog(HOSTILE_OFFER));

  @Test
  void escapesEveryTextFieldOfTheOffer() {
    String page = controller.landingPage(OFFER_ID, null).getBody();

    assertThat(page)
        .doesNotContain("<script>")
        .doesNotContain("<img")
        .doesNotContain("<b>")
        .contains("&lt;script&gt;alert(&#39;title&#39;)&lt;/script&gt;")
        .contains("&lt;b&gt;Firma&lt;/b&gt; &amp; Co")
        .contains("&quot;&gt;&lt;img src=x onerror=alert(1)&gt;");
  }

  @Test
  void tellsCachesThatThePageVariesByLanguage() {
    ResponseEntity<String> response = controller.landingPage(OFFER_ID, "en");

    assertThat(response.getHeaders().getVary()).containsExactly(HttpHeaders.ACCEPT_LANGUAGE);
  }

  /** A catalog with one offer, so the test needs no database. */
  private record SingleOfferCatalog(Offer offer) implements OfferCatalog {

    @Override
    public List<Offer> all() {
      return List.of(offer);
    }

    @Override
    public Optional<Offer> find(UUID offerId) {
      return offer.offerId().equals(offerId) ? Optional.of(offer) : Optional.empty();
    }
  }
}
