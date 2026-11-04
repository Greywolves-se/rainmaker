package se.rainmaker.contentintent.api;

/**
 * Why the article got its zone, in both languages of the editor view (#145, decision D-9).
 *
 * @param sv the reason in Swedish
 * @param en the reason in English
 */
public record Reason(String sv, String en) {

  /** Both languages are required and must not be blank. */
  public Reason {
    if (sv == null || sv.isBlank() || en == null || en.isBlank()) {
      throw new IllegalArgumentException("A reason needs non-blank Swedish and English text");
    }
  }
}
