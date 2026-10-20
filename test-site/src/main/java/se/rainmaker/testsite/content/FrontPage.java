package se.rainmaker.testsite.content;

import static se.rainmaker.testsite.content.JsonFields.isAbsent;
import static se.rainmaker.testsite.content.JsonFields.requireTextList;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Editorial choices for the front page, from {@code content/front-page.json}.
 *
 * <p>On a real news site an editor decides the top story - it is not simply the newest article.
 * This file plays that editor. Every id is checked against the articles at start-up.
 *
 * @param lead the big top story
 * @param breaking the article in the red "JUST NU" bar, or null for no bar
 * @param mostRead the "Mest läst" list, in order (made up - there is no tracking)
 */
public record FrontPage(Article lead, Article breaking, List<Article> mostRead) {

  private static final String SOURCE = "front-page.json";

  /** Makes the list unmodifiable. */
  public FrontPage {
    mostRead = List.copyOf(mostRead);
  }

  /** The breaking-news article, if there is one. */
  public Optional<Article> breakingNews() {
    return Optional.ofNullable(breaking);
  }

  /**
   * Parses and validates front-page.json.
   *
   * @throws ContentValidationException if the file is malformed or names unknown articles
   */
  public static FrontPage parse(JsonNode json, ArticleRepository articles) {
    if (json == null || !json.isObject()) {
      throw new ContentValidationException(SOURCE, "must be a JSON object");
    }
    Article lead = requireArticle(json.get("leadArticleId"), "leadArticleId", articles);
    Article breaking =
        isAbsent(json, "breakingArticleId")
            ? null
            : requireArticle(json.get("breakingArticleId"), "breakingArticleId", articles);

    List<String> mostReadIds =
        requireTextList(json, "mostReadArticleIds", SOURCE, "mostReadArticleIds");
    if (new HashSet<>(mostReadIds).size() != mostReadIds.size()) {
      throw new ContentValidationException(SOURCE, "\"mostReadArticleIds\" contains duplicates");
    }
    List<Article> mostRead =
        mostReadIds.stream().map(id -> findArticle(id, "mostReadArticleIds", articles)).toList();
    return new FrontPage(lead, breaking, mostRead);
  }

  private static Article requireArticle(JsonNode id, String field, ArticleRepository articles) {
    if (id == null || !id.isString()) {
      throw new ContentValidationException(SOURCE, '"' + field + "\" must be an article id");
    }
    return findArticle(id.asString(), field, articles);
  }

  private static Article findArticle(String id, String field, ArticleRepository articles) {
    return articles
        .findById(id)
        .orElseThrow(
            () ->
                new ContentValidationException(
                    SOURCE, '"' + field + "\" refers to unknown article \"" + id + '"'));
  }
}
