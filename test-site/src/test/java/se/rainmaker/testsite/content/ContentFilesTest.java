package se.rainmaker.testsite.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import se.rainmaker.testsite.config.SiteProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real content in content/: is it valid, and does it cover what Rainmaker needs to be tested
 * against.
 */
class ContentFilesTest {

  private final ContentConfiguration content =
      new ContentConfiguration(
          new SiteProperties("http://localhost:3000", Path.of("content"), null), new JsonMapper());
  private final SiteContent siteContent = content.liveContent().current();
  private final ArticleRepository articles = siteContent.articles();

  @Test
  void hasBetweenEightAndTwelveArticles() {
    assertThat(articles.all()).hasSizeBetween(8, 12);
  }

  @Test
  void coversAllThreeBrandSafetyZones() {
    Set<BrandSafetyZone> zones =
        articles.all().stream().map(Article::expectedZone).collect(Collectors.toSet());

    assertThat(zones).containsExactlyInAnyOrder(BrandSafetyZone.values());
  }

  @Test
  void hasSomePlusArticlesButNotOnlyPlus() {
    assertThat(articles.all()).anyMatch(Article::premium).anyMatch(article -> !article.premium());
  }

  @Test
  void neverUpdatesAnArticleBeforeItWasPublished() {
    for (Article article : articles.all()) {
      article.lastUpdate().ifPresent(update -> assertThat(update).isAfter(article.publishedAt()));
    }
  }

  @Test
  void frontPageAndAdsFilesAreValid() {
    FrontPage frontPage = siteContent.frontPage();

    assertThat(frontPage.breakingNews()).isPresent();
    assertThat(frontPage.mostRead()).isNotEmpty();
    assertThat(content.adInventory().creatives()).isNotEmpty();
  }

  @Test
  void everySectionHasTranslatedName() throws IOException {
    Properties messages = new Properties();
    try (InputStream file = getClass().getResourceAsStream("/messages.properties")) {
      messages.load(new InputStreamReader(file, StandardCharsets.UTF_8));
    }

    for (Section section : Section.values()) {
      assertThat(messages.getProperty(section.messageKey())).as(section.id()).isNotBlank();
    }
  }
}
