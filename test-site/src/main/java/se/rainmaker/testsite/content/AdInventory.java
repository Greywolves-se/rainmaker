package se.rainmaker.testsite.content;

import static se.rainmaker.testsite.content.JsonFields.requireNonEmptyArray;
import static se.rainmaker.testsite.content.JsonFields.requireObject;
import static se.rainmaker.testsite.content.JsonFields.requireText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

/**
 * All fake ads, and the rule for which ad goes in which slot.
 *
 * <p>Real news sites are full of ads from an ad server (usually Google Ad Manager). Rainmaker's
 * panel has to live next to them without breaking or shifting the page, so the test site has ads
 * too.
 */
public final class AdInventory {

  private static final String SOURCE = "ads.json";

  private final List<AdCreative> creatives;

  /**
   * Creates the inventory.
   *
   * @throws ContentValidationException if it is empty or two ads share an id
   */
  public AdInventory(List<AdCreative> creatives) {
    if (creatives.isEmpty()) {
      throw new ContentValidationException(SOURCE, "there must be at least one creative");
    }
    if (new HashSet<>(creatives.stream().map(AdCreative::id).toList()).size() != creatives.size()) {
      throw new ContentValidationException(SOURCE, "creative ids must be unique");
    }
    this.creatives = List.copyOf(creatives);
  }

  /** All creatives, in file order. */
  public List<AdCreative> creatives() {
    return creatives;
  }

  /**
   * Picks the ad for a slot. Ads targeted at the section come first (simple contextual targeting);
   * the slot number rotates through them so two slots on one page show different ads. Deterministic
   * on purpose: the same page always gets the same ads, which keeps tests stable.
   *
   * @param section the page's section, or null on the front page
   * @param slotNumber position of the slot on the page, starting at 0
   */
  public AdCreative pick(Section section, int slotNumber) {
    List<AdCreative> ordered =
        Stream.concat(
                creatives.stream().filter(creative -> isTargeted(creative, section)),
                creatives.stream().filter(creative -> !isTargeted(creative, section)))
            .toList();
    return ordered.get(Math.floorMod(slotNumber, ordered.size()));
  }

  private static boolean isTargeted(AdCreative creative, Section section) {
    return section != null && creative.targets(section);
  }

  /**
   * Parses and validates ads.json.
   *
   * @throws ContentValidationException if a field is missing or wrong
   */
  public static AdInventory parse(JsonNode json) {
    requireObject(json, SOURCE, "ads");
    JsonNode items = requireNonEmptyArray(json, "creatives", SOURCE);
    List<AdCreative> creatives = new ArrayList<>();
    for (int index = 0; index < items.size(); index++) {
      creatives.add(parseCreative(items.get(index), "creatives[" + index + "]"));
    }
    return new AdInventory(creatives);
  }

  private static AdCreative parseCreative(JsonNode json, String path) {
    requireObject(json, SOURCE, path);
    String id = requireText(json, "id", SOURCE, path + ".id");
    if (!ArticleParser.SLUG_PATTERN.matcher(id).matches()) {
      throw new ContentValidationException(
          SOURCE, '"' + path + ".id\" must be lower-case letters, digits and hyphens");
    }
    String theme = requireText(json, "theme", SOURCE, path + ".theme");
    if (!AdCreative.THEMES.contains(theme)) {
      throw new ContentValidationException(
          SOURCE, '"' + path + ".theme\" must be one of " + AdCreative.THEMES);
    }
    JsonNode sectionIds = json.get("sections");
    if (sectionIds == null || !sectionIds.isArray()) {
      throw new ContentValidationException(SOURCE, '"' + path + ".sections\" must be a list");
    }
    List<Section> sections = new ArrayList<>();
    for (JsonNode sectionId : sectionIds) {
      sections.add(
          Section.fromId(sectionId.isString() ? sectionId.asString() : "")
              .orElseThrow(
                  () ->
                      new ContentValidationException(
                          SOURCE, '"' + path + ".sections\" has unknown section " + sectionId)));
    }
    return new AdCreative(
        id,
        requireText(json, "advertiser", SOURCE, path + ".advertiser"),
        requireText(json, "headline", SOURCE, path + ".headline"),
        requireText(json, "text", SOURCE, path + ".text"),
        requireText(json, "callToAction", SOURCE, path + ".callToAction"),
        theme,
        sections);
  }
}
