package se.rainmaker.testsite.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.rainmaker.testsite.content.ContentTestData.article;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArticleRepositoryTest {

  private final Article oldTravel = article("a", Section.RESOR, "2026-10-10T08:00:00+02:00");
  private final Article newTravel = article("b", Section.RESOR, "2026-10-12T08:00:00+02:00");
  private final Article sport = article("c", Section.SPORT, "2026-10-13T08:00:00+02:00");
  private final Article culture = article("d", Section.KULTUR, "2026-10-11T08:00:00+02:00");
  private final ArticleRepository repository =
      new ArticleRepository(List.of(oldTravel, sport, culture, newTravel));

  @Test
  void listsArticlesNewestFirst() {
    assertThat(repository.all()).containsExactly(sport, newTravel, culture, oldTravel);
  }

  @Test
  void comparesPublishTimesAcrossTimeZones() {
    // 09:00 in Stockholm (+02:00) is earlier than 08:00 UTC.
    Article stockholm = article("x", Section.SPORT, "2026-10-13T09:00:00+02:00");
    Article utc = article("y", Section.SPORT, "2026-10-13T08:00:00Z");

    assertThat(new ArticleRepository(List.of(stockholm, utc)).all())
        .containsExactly(utc, stockholm);
  }

  @Test
  void findsArticlesByIdAndSlug() {
    assertThat(repository.findById("c")).contains(sport);
    assertThat(repository.findBySlug("d")).contains(culture);
    assertThat(repository.findBySlug("finns-inte")).isEmpty();
  }

  @Test
  void filtersBySection() {
    assertThat(repository.inSection(Section.RESOR)).containsExactly(newTravel, oldTravel);
    assertThat(repository.inSection(Section.MOTOR)).isEmpty();
  }

  @Test
  void suggestsSameSectionFirstAndNeverTheArticleItself() {
    assertThat(repository.related(oldTravel, 3)).containsExactly(newTravel, sport, culture);
  }

  @Test
  void reportsTheLatestPublishTime() {
    assertThat(repository.latestPublishedAt()).contains(sport.publishedAt());
    assertThat(new ArticleRepository(List.of()).latestPublishedAt()).isEmpty();
  }

  @Test
  void rejectsDuplicateIdsAndSlugs() {
    assertThatThrownBy(() -> new ArticleRepository(List.of(sport, sport)))
        .isInstanceOf(ContentValidationException.class)
        .hasMessageContaining("duplicate id");
  }
}
