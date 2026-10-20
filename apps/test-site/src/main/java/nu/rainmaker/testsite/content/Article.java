package nu.rainmaker.testsite.content;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * One news article, as stored in {@code content/articles/*.json}.
 *
 * @param id stable id, e.g. "vb-0001"; RSS guid and evaluation label key
 * @param slug URL part: /artikel/&lt;slug&gt;
 * @param section the site section
 * @param expectedZone the zone a human editor would give the article - the "right answer" for
 *     testing the AI classification (S6). Deliberately NOT put in the article HTML, so the
 *     classifier cannot read it; only published in /eval/labels.csv
 * @param premium PLUS article: the end of the text is behind the (fake) paywall. The full text is
 *     still in the HTML, so Content Ingestion (S5) can read it
 * @param kicker short label above the headline ("vinjett"), e.g. "RESOR"
 * @param headline the headline
 * @param lead the bold introduction ("ingress")
 * @param author who wrote it
 * @param publishedAt when it was published, with time zone
 * @param updatedAt when it was last updated, or null
 * @param body the article text, block by block
 */
public record Article(
    String id,
    String slug,
    Section section,
    BrandSafetyZone expectedZone,
    boolean premium,
    String kicker,
    String headline,
    String lead,
    Author author,
    OffsetDateTime publishedAt,
    OffsetDateTime updatedAt,
    List<ArticleBlock> body) {

  /** Makes the body list unmodifiable. */
  public Article {
    body = List.copyOf(body);
  }

  /** When the article was last updated, if it was. */
  public Optional<OffsetDateTime> lastUpdate() {
    return Optional.ofNullable(updatedAt);
  }

  /** The last change: the update time, or the publish time if never updated. */
  public OffsetDateTime lastModified() {
    return updatedAt != null ? updatedAt : publishedAt;
  }

  /** The relative URL of the article page. */
  public String path() {
    return "/artikel/" + slug;
  }

  /**
   * Who wrote the article.
   *
   * @param name full name
   * @param role job title, e.g. "Resereporter"
   */
  public record Author(String name, String role) {}
}
