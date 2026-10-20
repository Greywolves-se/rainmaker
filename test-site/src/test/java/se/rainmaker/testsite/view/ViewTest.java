package se.rainmaker.testsite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static se.rainmaker.testsite.content.ContentTestData.article;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.rainmaker.testsite.content.Article;
import se.rainmaker.testsite.content.ArticleBlock;
import se.rainmaker.testsite.content.Section;

/** Formatting helpers and the article body layout (in-article ad and paywall). */
class ViewTest {

  private final ViewHelper view = new ViewHelper();

  @Test
  void showsSwedishTimeWhateverTheServerTimeZone() {
    // 07:42 UTC is 09:42 in Stockholm (summer time).
    OffsetDateTime utc = OffsetDateTime.parse("2026-10-13T07:42:00Z");

    assertThat(view.time(utc)).isEqualTo("09.42");
    assertThat(view.date(utc)).isEqualTo("13 okt. 2026");
    assertThat(view.dayAndMonth(utc)).isEqualTo("13 okt.");
  }

  @Test
  void switchesToWinterTimeAtTheEndOfOctober() {
    assertThat(view.time(OffsetDateTime.parse("2026-11-02T07:42:00Z"))).isEqualTo("08.42");
  }

  @Test
  void roundsReadingTimeUpAndShowsAtLeastOneMinute() {
    assertThat(view.readingTimeMinutes(withWords(1))).isEqualTo(1);
    assertThat(view.readingTimeMinutes(withWords(199))).isEqualTo(1);
    assertThat(view.readingTimeMinutes(withWords(200))).isEqualTo(1);
    assertThat(view.readingTimeMinutes(withWords(201))).isEqualTo(2);
  }

  @Test
  void makesInitialsForTheByline() {
    assertThat(view.initials("Amira Haddad")).isEqualTo("AH");
    assertThat(view.initials("  Johan  Ekberg Andersson ")).isEqualTo("JE");
  }

  @Test
  void hidesKickerThatOnlyRepeatsTheSection() {
    Article article = article("a", Section.RESOR, "2026-10-13T08:00:00+02:00");

    assertThat(view.showKicker(article, "Kicker")).isFalse();
    assertThat(view.showKicker(article, "Resor")).isTrue();
  }

  /** Lead "x" (1 word) plus a body with words-1 words. */
  private static Article withWords(int words) {
    Article base = article("a", Section.RESOR, "2026-10-13T08:00:00+02:00");
    String text = String.join(" ", Collections.nCopies(Math.max(0, words - 1), "ord"));
    List<ArticleBlock> body = List.of(new ArticleBlock.Paragraph(text.isEmpty() ? " " : text));
    return new Article(
        base.id(),
        base.slug(),
        base.section(),
        base.expectedZone(),
        false,
        base.kicker(),
        base.headline(),
        "x",
        base.author(),
        base.publishedAt(),
        null,
        body);
  }

  private static ArticleBodyBuilder bodyBuilder() {
    AdSlots adSlots = mock(AdSlots.class);
    when(adSlots.slot(any(), any(), anyInt(), anyBoolean()))
        .thenAnswer(call -> new AdSlot(call.getArgument(0), "ad", call.<Boolean>getArgument(3)));
    return new ArticleBodyBuilder(adSlots);
  }

  private static Article withParagraphs(int count, boolean premium) {
    Article base = article("a", Section.MOTOR, "2026-10-13T08:00:00+02:00", count);
    return new Article(
        base.id(),
        base.slug(),
        base.section(),
        base.expectedZone(),
        premium,
        base.kicker(),
        base.headline(),
        base.lead(),
        base.author(),
        base.publishedAt(),
        null,
        base.body());
  }

  @Test
  void putsAnUnreservedAdAfterTheThirdBlock() {
    ArticleBody body = bodyBuilder().build(withParagraphs(6, false));

    assertThat(body.free()).hasSize(7);
    assertThat(body.free().get(3).isAd()).isTrue();
    assertThat(body.free().get(3).ad().reserveSpace()).isFalse();
    assertThat(body.free().get(3).ad().format()).isEqualTo(AdSlot.Format.RECTANGLE);
    assertThat(body.paywalled()).isFalse();
  }

  @Test
  void leavesShortArticlesWithoutInArticleAd() {
    ArticleBody body = bodyBuilder().build(withParagraphs(4, false));

    assertThat(body.free()).hasSize(4).noneMatch(ArticleBody.Item::isAd);
  }

  @Test
  void splitsPlusArticlesAfterFourBlocksPlusTheAd() {
    ArticleBody body = bodyBuilder().build(withParagraphs(8, true));

    assertThat(body.paywalled()).isTrue();
    assertThat(body.free()).hasSize(5);
    assertThat(body.locked()).hasSize(4).noneMatch(ArticleBody.Item::isAd);
  }

  @Test
  void doesNotPaywallVeryShortPlusArticles() {
    assertThat(bodyBuilder().build(withParagraphs(3, true)).paywalled()).isFalse();
  }
}
