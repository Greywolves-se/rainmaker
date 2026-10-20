package se.rainmaker.testsite.content;

import static se.rainmaker.testsite.content.JsonFields.isAbsent;
import static se.rainmaker.testsite.content.JsonFields.requireNonEmptyArray;
import static se.rainmaker.testsite.content.JsonFields.requireObject;
import static se.rainmaker.testsite.content.JsonFields.requireText;
import static se.rainmaker.testsite.content.JsonFields.requireTextList;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import se.rainmaker.testsite.content.Article.Author;
import tools.jackson.databind.JsonNode;

/**
 * Turns one parsed article JSON file into an {@link Article}, checking every field.
 *
 * <p>Validation runs once at start-up: a broken article file stops the site with a message that
 * names the file and the field, instead of producing a half-broken page later.
 */
public final class ArticleParser {

  /** Lower-case letters, digits and single hyphens, e.g. "mallorca-i-host". */
  static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  /**
   * An ISO date-time WITH an explicit offset (Z or +02:00), so a date never depends on the time
   * zone of the machine that reads it.
   */
  private static final Pattern DATE_TIME_PATTERN =
      Pattern.compile(
          "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:\\d{2})$");

  private ArticleParser() {}

  /**
   * Parses and validates one article.
   *
   * @param json the parsed JSON
   * @param source used in error messages, usually the file name
   * @return the article
   * @throws ContentValidationException if a field is missing or wrong
   */
  public static Article parse(JsonNode json, String source) {
    requireObject(json, source, "article");

    String id = requireText(json, "id", source, "id");
    if (!SLUG_PATTERN.matcher(id).matches()) {
      throw new ContentValidationException(source, "\"id\" has an invalid format: " + id);
    }
    String slug = requireText(json, "slug", source, "slug");
    if (!SLUG_PATTERN.matcher(slug).matches()) {
      throw new ContentValidationException(
          source, "\"slug\" must be lower-case letters, digits and hyphens: " + slug);
    }
    String sectionId = requireText(json, "section", source, "section");
    Section section =
        Section.fromId(sectionId)
            .orElseThrow(
                () ->
                    new ContentValidationException(
                        source, "\"section\" is not a known section: " + sectionId));
    String zoneId = requireText(json, "expectedZone", source, "expectedZone");
    BrandSafetyZone zone =
        BrandSafetyZone.fromId(zoneId)
            .orElseThrow(
                () ->
                    new ContentValidationException(
                        source, "\"expectedZone\" must be green, yellow or red: " + zoneId));

    return new Article(
        id,
        slug,
        section,
        zone,
        parsePremium(json, source),
        requireText(json, "kicker", source, "kicker"),
        requireText(json, "headline", source, "headline"),
        requireText(json, "lead", source, "lead"),
        parseAuthor(json.get("author"), source),
        parseDateTime(json, "publishedAt", source),
        isAbsent(json, "updatedAt") ? null : parseDateTime(json, "updatedAt", source),
        parseBody(json, source));
  }

  private static boolean parsePremium(JsonNode json, String source) {
    if (isAbsent(json, "premium")) {
      return false;
    }
    JsonNode premium = json.get("premium");
    if (!premium.isBoolean()) {
      throw new ContentValidationException(source, "\"premium\" must be true or false");
    }
    return premium.asBoolean();
  }

  private static Author parseAuthor(JsonNode json, String source) {
    requireObject(json, source, "author");
    return new Author(
        requireText(json, "name", source, "author.name"),
        requireText(json, "role", source, "author.role"));
  }

  private static OffsetDateTime parseDateTime(JsonNode json, String field, String source) {
    JsonNode value = json.get(field);
    String text = value != null && value.isString() ? value.asString() : "";
    if (!DATE_TIME_PATTERN.matcher(text).matches()) {
      throw new ContentValidationException(
          source,
          '"' + field + "\" must be an ISO date-time with offset, e.g. 2026-10-13T09:42:00+02:00");
    }
    try {
      return OffsetDateTime.parse(text);
    } catch (DateTimeParseException exception) {
      throw new ContentValidationException(
          source, '"' + field + "\" is not a real date: " + text, exception);
    }
  }

  private static List<ArticleBlock> parseBody(JsonNode json, String source) {
    JsonNode body = requireNonEmptyArray(json, "body", source);
    List<ArticleBlock> blocks = new ArrayList<>();
    for (int index = 0; index < body.size(); index++) {
      blocks.add(parseBlock(body.get(index), source, "body[" + index + "]"));
    }
    if (blocks.stream().noneMatch(ArticleBlock.Paragraph.class::isInstance)) {
      throw new ContentValidationException(source, "\"body\" must contain at least one paragraph");
    }
    return blocks;
  }

  private static ArticleBlock parseBlock(JsonNode json, String source, String path) {
    requireObject(json, source, path);
    JsonNode typeNode = json.path("type");
    String type = typeNode.isString() ? typeNode.asString() : "";
    return switch (type) {
      case "paragraph" ->
          new ArticleBlock.Paragraph(requireText(json, "text", source, path + ".text"));
      case "subheading" ->
          new ArticleBlock.Subheading(requireText(json, "text", source, path + ".text"));
      case "quote" ->
          new ArticleBlock.Quote(
              requireText(json, "text", source, path + ".text"),
              requireText(json, "source", source, path + ".source"));
      case "factbox" ->
          new ArticleBlock.Factbox(
              requireText(json, "title", source, path + ".title"),
              requireTextList(json, "items", source, path + ".items"));
      default ->
          throw new ContentValidationException(source, '"' + path + ".type\" is unknown: " + type);
    };
  }
}
