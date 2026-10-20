package nu.rainmaker.testsite.content;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/**
 * The site's sections, in the order they appear in the menu.
 *
 * <p>The id is used in URLs ({@code /sektion/resor}) and in the content files. The visible name
 * comes from the translation file ({@code section.resor=Resor}), never from here.
 */
public enum Section {
  NYHETER("nyheter"),
  SPORT("sport"),
  RESOR("resor"),
  KULTUR("kultur"),
  MAT("mat"),
  BOSTAD("bostad"),
  MOTOR("motor"),
  HALSA("halsa"),
  EKONOMI("ekonomi");

  private final String id;

  Section(String id) {
    this.id = id;
  }

  /** The id used in URLs and content files, e.g. "resor". Also used in JSON output. */
  @JsonValue
  public String id() {
    return id;
  }

  /** The translation key of the visible name, e.g. "section.resor". */
  public String messageKey() {
    return "section." + id;
  }

  /** Finds a section by its id. */
  public static Optional<Section> fromId(String id) {
    return Arrays.stream(values()).filter(section -> section.id.equals(id)).findFirst();
  }
}
