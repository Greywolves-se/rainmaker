package nu.rainmaker.testsite.view;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nu.rainmaker.testsite.content.Article;
import nu.rainmaker.testsite.content.ArticleBlock;
import org.springframework.stereotype.Component;

/**
 * Formatting helpers for the templates, used as {@code ${@view.time(article.publishedAt())}}.
 *
 * <p>Times are always shown in Swedish time (Europe/Stockholm), whatever time zone the server runs
 * in. Cloud servers usually run in UTC; without this every time would be one or two hours off. The
 * surrounding words ("Publicerad ... kl. ...") come from the translation file.
 */
@Component("view")
public class ViewHelper {

  /** Average reading speed used for "Lästid". */
  static final int WORDS_PER_MINUTE = 200;

  private static final ZoneId SITE_TIME_ZONE = ZoneId.of("Europe/Stockholm");
  private static final Locale SWEDISH = Locale.forLanguageTag("sv-SE");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", SWEDISH);
  private static final DateTimeFormatter DAY_AND_MONTH =
      DateTimeFormatter.ofPattern("d MMM", SWEDISH);
  // Swedish newspapers write "kl. 09.42", not 09:42.
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH.mm", SWEDISH);

  /** "13 okt. 2026". */
  public String date(OffsetDateTime dateTime) {
    return DATE.format(dateTime.atZoneSameInstant(SITE_TIME_ZONE));
  }

  /** "13 okt.". */
  public String dayAndMonth(OffsetDateTime dateTime) {
    return DAY_AND_MONTH.format(dateTime.atZoneSameInstant(SITE_TIME_ZONE));
  }

  /** "09.42". */
  public String time(OffsetDateTime dateTime) {
    return TIME.format(dateTime.atZoneSameInstant(SITE_TIME_ZONE));
  }

  /** ISO timestamp for the {@code datetime} attribute of {@code <time>}. */
  public String iso(OffsetDateTime dateTime) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
  }

  /** Minutes to read the lead and body, rounded up, at least 1. */
  public int readingTimeMinutes(Article article) {
    long words =
        Stream.concat(
                Stream.of(article.lead()),
                article.body().stream().map(ArticleBlock::texts).flatMap(List::stream))
            .flatMap(text -> Arrays.stream(text.trim().split("\\s+")))
            .filter(word -> !word.isEmpty())
            .count();
    return (int) Math.max(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE);
  }

  /** "Amira Haddad" becomes "AH", for the round badge in the byline. */
  public String initials(String name) {
    return Arrays.stream(name.trim().split("\\s+"))
        .filter(part -> !part.isEmpty())
        .limit(2)
        .map(part -> part.substring(0, 1).toUpperCase(SWEDISH))
        .collect(Collectors.joining());
  }

  /** False when the kicker only repeats the section name (avoids "RESOR RESOR"). */
  public boolean showKicker(Article article, String sectionName) {
    return !article.kicker().toLowerCase(SWEDISH).equals(sectionName.toLowerCase(SWEDISH));
  }
}
