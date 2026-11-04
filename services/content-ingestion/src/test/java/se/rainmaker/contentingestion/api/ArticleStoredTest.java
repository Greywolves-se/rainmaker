package se.rainmaker.contentingestion.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArticleStoredTest {

  @Test
  void neverPutsTheBodyTextInItsDescription() {
    ArticleStored stored =
        new ArticleStored(
            UUID.fromString("0192f3c2-0000-7000-8000-000000000001"),
            2,
            "http://localhost:3000/artikel/badrum",
            "Badrummet",
            "Hemlig brödtext",
            "bostad",
            "sv",
            Instant.parse("2026-10-10T05:45:00Z"),
            null,
            "c0a1");

    // The text stays inside G2 and is never logged (contract section 3.1).
    assertThat(stored.toString())
        .doesNotContain("Hemlig brödtext")
        .contains("version=2", "http://localhost:3000/artikel/badrum", "c0a1");
  }
}
