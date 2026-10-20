package nu.rainmaker.testsite.content;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

/** Builds small articles for unit tests. */
public final class ContentTestData {

  private ContentTestData() {}

  /** An article with the given id, section, publish time and number of paragraphs. */
  public static Article article(String id, Section section, String publishedAt, int paragraphs) {
    List<ArticleBlock> body =
        IntStream.rangeClosed(1, paragraphs)
            .<ArticleBlock>mapToObj(number -> new ArticleBlock.Paragraph("Stycke " + number))
            .toList();
    return new Article(
        id,
        id,
        section,
        BrandSafetyZone.GREEN,
        false,
        "KICKER",
        "Rubrik " + id,
        "Ingress",
        new Article.Author("Test Testsson", "Reporter"),
        OffsetDateTime.parse(publishedAt),
        null,
        body);
  }

  /** A short article in a section. */
  public static Article article(String id, Section section, String publishedAt) {
    return article(id, section, publishedAt, 1);
  }
}
