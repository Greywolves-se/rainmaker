package se.rainmaker.trackingclick.click;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import se.rainmaker.trackingclick.click.ClickErrorPages.Reason;

/** The click error pages: Swedish unless the browser asks for English (#153, contract 7.5). */
class ClickErrorPagesTest {

  private final ClickErrorPages pages = new ClickErrorPages();

  @ParameterizedTest
  @CsvSource(
      nullValues = "none",
      value = {
        "none, sv",
        "'', sv",
        "de, sv",
        "sv-SE, sv",
        "en, en",
        "'en-GB,en;q=0.9,sv;q=0.8', en",
        "'sv;q=0.9,en;q=0.5', sv",
        "'not;a=valid header', sv"
      })
  void picksEnglishOnlyWhenAsked(String acceptLanguage, String expected) {
    Locale language = ClickErrorPages.language(acceptLanguage);

    assertThat(language.getLanguage()).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(Reason.class)
  void everyReasonHasTextInBothLanguages(Reason reason) {
    String swedish = pages.page(reason, "sv");
    String english = pages.page(reason, "en");

    assertThat(swedish).contains("<html lang=\"sv\">");
    assertThat(english).contains("<html lang=\"en\">");
    // A key missing from messages_en.properties silently falls back to the Swedish text, so the
    // heading and the text are compared one by one.
    assertThat(between(english, "<h1>", "</h1>")).isNotEqualTo(between(swedish, "<h1>", "</h1>"));
    assertThat(between(english, "<p>", "</p>")).isNotEqualTo(between(swedish, "<p>", "</p>"));
  }

  private static String between(String page, String start, String end) {
    int from = page.indexOf(start) + start.length();
    return page.substring(from, page.indexOf(end, from));
  }
}
