package se.rainmaker.publishercontrol.links;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.rainmaker.advertisermarketplace.api.Offer;
import se.rainmaker.advertisermarketplace.api.OfferCatalog;
import se.rainmaker.publishercontrol.articles.CurrentArticle;
import se.rainmaker.publishercontrol.articles.G2ArticlesClient;
import se.rainmaker.publishercontrol.database.PublisherControlDatabaseConfiguration;

/**
 * The links an article may show (#150, contract 5 and 7.3). A link reaches the widget only when all
 * of these hold:
 *
 * <ol>
 *   <li>the suggestion is approved in G3;
 *   <li>it belongs to the article's latest version;
 *   <li>G2 says, at this moment, that this version is classified + green.
 * </ol>
 *
 * <p>G2 is asked on every call, not G3's copy, which may be one poll behind. If G2 can't answer,
 * {@link se.rainmaker.publishercontrol.articles.ArticlesUnavailableException} reaches the caller:
 * no links, never the last known answer (fail closed for brand safety).
 */
@Component
class ApprovedLinks {

  private static final Logger LOG = LoggerFactory.getLogger(ApprovedLinks.class);

  private final G2ArticlesClient g2;
  private final JdbcClient database;
  private final OfferCatalog offers;

  ApprovedLinks(
      G2ArticlesClient g2,
      @Qualifier(PublisherControlDatabaseConfiguration.JDBC_CLIENT) JdbcClient database,
      OfferCatalog offers) {
    this.g2 = g2;
    this.database = database;
    this.offers = offers;
  }

  /** The approved links for the article at this address, at most three, by rank. */
  ApprovedLinksAnswer forArticle(String articleUrl, String correlationId) {
    Optional<CurrentArticle> found = g2.current(articleUrl, correlationId);
    if (found.isEmpty()) {
      return new ApprovedLinksAnswer(null, articleUrl, List.of());
    }
    CurrentArticle article = found.get();
    List<ApprovedLinksAnswer.Link> links =
        article.green() ? approved(article) : List.<ApprovedLinksAnswer.Link>of();
    return new ApprovedLinksAnswer(article.articleId(), article.url(), links);
  }

  /** Rules 1 and 2: approved, and of the version G2 has now. */
  private List<ApprovedLinksAnswer.Link> approved(CurrentArticle article) {
    return database
        .sql(
            """
            SELECT offer_id, rank FROM suggestion
            WHERE article_id = ? AND article_version = ? AND status = 'approved'
            ORDER BY rank LIMIT 3
            """)
        .params(article.articleId(), article.version())
        .query((row, number) -> link(row.getObject("offer_id", UUID.class), row.getInt("rank")))
        .list()
        .stream()
        .flatMap(Optional::stream)
        .toList();
  }

  /** An offer that no longer exists can't be shown; it is left out and logged. */
  private Optional<ApprovedLinksAnswer.Link> link(UUID offerId, int rank) {
    Optional<Offer> offer = offers.find(offerId);
    if (offer.isEmpty()) {
      LOG.warn("Approved offer {} no longer exists; its link is left out", offerId);
    }
    return offer.map(
        found ->
            new ApprovedLinksAnswer.Link(
                found.offerId(),
                rank,
                found.advertiser(),
                found.title(),
                found.text(),
                found.destinationUrl()));
  }
}
