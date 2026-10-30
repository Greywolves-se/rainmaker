package se.rainmaker.widgetdelivery.links;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The widget API's settings are checked at start-up, so a typo can't open CORS to any site. */
class WidgetApiPropertiesTest {

  @Test
  void acceptsSiteOrigins() {
    WidgetApiProperties properties =
        new WidgetApiProperties(List.of("http://localhost:3000", "https://www.example.se"));

    assertThat(properties.allowedOrigins())
        .containsExactly("http://localhost:3000", "https://www.example.se");
  }

  @Test
  void refusesNoOrigins() {
    assertThatThrownBy(() -> new WidgetApiProperties(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allowed-origins");
    assertThatThrownBy(() -> new WidgetApiProperties(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allowed-origins");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "*",
        "localhost:3000",
        "ftp://localhost:3000",
        "http://localhost:3000/",
        "http://localhost:3000/artikel",
        "http://localhost:3000?x=1",
        "http://user@localhost:3000",
        "not an origin"
      })
  void refusesValueThatIsNotOneOrigin(String origin) {
    assertThatThrownBy(() -> new WidgetApiProperties(List.of(origin)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allowed-origins");
  }
}
