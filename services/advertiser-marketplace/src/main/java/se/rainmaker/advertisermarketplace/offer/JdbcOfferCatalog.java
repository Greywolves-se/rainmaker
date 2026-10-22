package se.rainmaker.advertisermarketplace.offer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.rainmaker.advertisermarketplace.api.Offer;
import se.rainmaker.advertisermarketplace.api.OfferCatalog;
import se.rainmaker.advertisermarketplace.database.AdvertiserMarketplaceDatabaseConfiguration;

/** The offers in S4's own database (#149). */
@Component
class JdbcOfferCatalog implements OfferCatalog {

  private static final String SELECT =
      "SELECT offer_id, advertiser, title, offer_text, category, cpc_ore FROM offer";

  private final JdbcClient database;
  private final String landingBaseUrl;

  /**
   * Creates the catalog.
   *
   * @param landingBaseUrl where the landing pages are served, e.g. {@code http://localhost:8083}
   */
  JdbcOfferCatalog(
      @Qualifier(AdvertiserMarketplaceDatabaseConfiguration.JDBC_CLIENT) JdbcClient database,
      @Value("${rainmaker.advertiser-marketplace.landing-base-url}") String landingBaseUrl) {
    this.database = database;
    this.landingBaseUrl = landingBaseUrl.replaceAll("/+$", "");
  }

  @Override
  public List<Offer> all() {
    return database.sql(SELECT + " ORDER BY offer_id").query(this::offer).list();
  }

  @Override
  public Optional<Offer> find(UUID offerId) {
    return database
        .sql(SELECT + " WHERE offer_id = :offerId")
        .param("offerId", offerId)
        .query(this::offer)
        .optional();
  }

  private Offer offer(ResultSet row, int rowNumber) throws SQLException {
    UUID offerId = row.getObject("offer_id", UUID.class);
    return new Offer(
        offerId,
        row.getString("advertiser"),
        row.getString("title"),
        row.getString("offer_text"),
        row.getString("category"),
        landingBaseUrl + LandingPageController.PATH_PREFIX + offerId,
        row.getInt("cpc_ore"));
  }
}
