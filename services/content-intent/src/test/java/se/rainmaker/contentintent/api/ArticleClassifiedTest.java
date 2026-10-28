package se.rainmaker.contentintent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The event matching listens to (#145, contract 6.2). */
class ArticleClassifiedTest {

  private static final UUID ARTICLE = UUID.randomUUID();

  @Test
  void carriesTheClassificationOfOneArticleVersion() {
    ArticleClassified event =
        new ArticleClassified(
            ARTICLE,
            2,
            "http://localhost:3000/artikel/a",
            "Rubrik",
            Classification.unclassified(),
            "c");

    assertThat(event.articleId()).isEqualTo(ARTICLE);
    assertThat(event.articleVersion()).isEqualTo(2);
    assertThat(event.classification().mayGetSuggestions()).isFalse();
  }

  @Test
  void needsArticleVersionAndClassification() {
    assertThatThrownBy(
            () -> new ArticleClassified(null, 1, "u", "h", Classification.unclassified(), "c"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ArticleClassified(ARTICLE, 0, "u", "h", Classification.unclassified(), "c"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArticleClassified(ARTICLE, 1, "u", "h", null, "c"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
