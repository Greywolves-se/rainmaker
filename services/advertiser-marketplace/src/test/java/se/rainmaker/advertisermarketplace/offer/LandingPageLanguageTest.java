package se.rainmaker.advertisermarketplace.offer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The landing page is Swedish unless the browser asks for English (#149). */
class LandingPageLanguageTest {

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
    Locale language = LandingPageController.language(acceptLanguage);

    assertThat(language.getLanguage()).isEqualTo(expected);
  }
}
