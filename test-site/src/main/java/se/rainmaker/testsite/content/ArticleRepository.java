package se.rainmaker.testsite.content;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/** All articles, newest first, with the look-ups the pages need. Immutable after creation. */
public final class ArticleRepository {

  private final List<Article> newestFirst;

  /**
   * Creates the repository.
   *
   * @throws ContentValidationException if two articles share an id or a slug
   */
  public ArticleRepository(List<Article> articles) {
    assertUnique(articles, Article::id, "id");
    assertUnique(articles, Article::slug, "slug");
    this.newestFirst =
        articles.stream().sorted(Comparator.comparing(Article::publishedAt).reversed()).toList();
  }

  /** All articles, newest first. */
  public List<Article> all() {
    return newestFirst;
  }

  /** The article with this id, if any. */
  public Optional<Article> findById(String id) {
    return newestFirst.stream().filter(article -> article.id().equals(id)).findFirst();
  }

  /** The article with this slug, if any. */
  public Optional<Article> findBySlug(String slug) {
    return newestFirst.stream().filter(article -> article.slug().equals(slug)).findFirst();
  }

  /** The articles in one section, newest first. */
  public List<Article> inSection(Section section) {
    return newestFirst.stream().filter(article -> article.section() == section).toList();
  }

  /**
   * "Läs också" suggestions: other articles from the same section first, then the newest articles
   * from the rest of the site. Never the article itself.
   */
  public List<Article> related(Article article, int limit) {
    Stream<Article> sameSection =
        newestFirst.stream()
            .filter(other -> !other.id().equals(article.id()))
            .filter(other -> other.section() == article.section());
    Stream<Article> otherSections =
        newestFirst.stream().filter(other -> other.section() != article.section());
    return Stream.concat(sameSection, otherSections).limit(limit).toList();
  }

  /** The newest publish time on the site, if there are articles. */
  public Optional<OffsetDateTime> latestPublishedAt() {
    return newestFirst.stream().findFirst().map(Article::publishedAt);
  }

  private static void assertUnique(
      List<Article> articles, Function<Article, String> field, String name) {
    Set<String> seen = new HashSet<>();
    for (Article article : articles) {
      String value = field.apply(article);
      if (!seen.add(value)) {
        throw new ContentValidationException("articles", "duplicate " + name + ": " + value);
      }
    }
  }
}
