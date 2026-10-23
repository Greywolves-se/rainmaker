package se.rainmaker.contentintent.api;

/** Where an article's classification stands (#145, section 3.3, decision D-4). */
public enum ClassificationStatus {
  /** Stored, not classified yet; also when the module's own call limits defer the call. */
  UNCLASSIFIED("unclassified"),
  /** Claude gave a valid answer. */
  CLASSIFIED("classified"),
  /** Timeout, refusal, invalid answer, access denied or retries used up. */
  FAILED("failed");

  private final String key;

  ClassificationStatus(String key) {
    this.key = key;
  }

  /** The value used in JSON and the database, e.g. {@code "classified"}. */
  public String key() {
    return key;
  }
}
