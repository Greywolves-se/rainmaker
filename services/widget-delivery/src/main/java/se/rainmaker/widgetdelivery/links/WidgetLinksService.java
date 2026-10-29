package se.rainmaker.widgetdelivery.links;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.rainmaker.trackingclick.api.ClickLinks;

/**
 * Turns G3's approved links for an article into the panel's links (#151, contract 7.4): at most
 * three, in rank order, each with a new signed click link (contract 7.5, D-6) in place of the
 * offer's address. Nothing is cached, so an article that turns red loses its links on the next
 * hover.
 */
@Service
class WidgetLinksService {

  private static final Logger LOG = LoggerFactory.getLogger(WidgetLinksService.class);

  /** The panel's size (contract 7.4). */
  private static final int MAX_LINKS = 3;

  private final G3ApprovedLinksClient g3;
  private final ClickLinks clickLinks;

  WidgetLinksService(G3ApprovedLinksClient g3, ClickLinks clickLinks) {
    this.g3 = g3;
    this.clickLinks = clickLinks;
  }

  /**
   * The article's links for the panel.
   *
   * @param articleUrl the article's canonical address (contract D-2)
   * @param correlationId sent to G3 (contract 7)
   * @throws ApprovedLinksUnavailableException when G3 can't answer: no links (fail closed)
   */
  WidgetLinks find(String articleUrl, String correlationId) {
    ApprovedLinks approved = g3.find(articleUrl, correlationId);
    if (approved.articleId() == null) {
      // An unknown article (contract 7.3); without its id there is nothing to put in a click link.
      return new WidgetLinks(articleUrl, List.of());
    }
    List<WidgetLinks.Link> links =
        approved.links().stream()
            .sorted(Comparator.comparingInt(ApprovedLink::rank))
            .flatMap(link -> signed(approved, link).stream())
            .limit(MAX_LINKS)
            .toList();
    LOG.info(
        "Widget links for articleId={}: {} of {} approved",
        approved.articleId(),
        links.size(),
        approved.links().size());
    return new WidgetLinks(articleUrl, links);
  }

  /**
   * A link whose destination can't be signed is left out, so one bad offer never costs the panel.
   */
  private Optional<WidgetLinks.Link> signed(ApprovedLinks approved, ApprovedLink link) {
    String clickUrl;
    try {
      clickUrl = clickLinks.create(approved.articleId(), link.offerId(), link.destinationUrl());
    } catch (IllegalArgumentException unusableDestination) {
      LOG.warn(
          "Left out offerId={} for articleId={}: {}",
          link.offerId(),
          approved.articleId(),
          unusableDestination.getMessage());
      return Optional.empty();
    }
    return Optional.of(
        new WidgetLinks.Link(
            link.offerId(), link.advertiser(), link.title(), link.text(), clickUrl));
  }
}
