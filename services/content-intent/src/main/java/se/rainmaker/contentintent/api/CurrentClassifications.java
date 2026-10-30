package se.rainmaker.contentintent.api;

import java.util.Collection;
import java.util.Map;

/**
 * The current classification of article versions, for modules inside G2 (#209; e.g. the articles
 * API, contract 7.1, which answers G3's poll of 100 articles every few seconds).
 */
public interface CurrentClassifications {

  /**
   * The current classification of each version, read in one query. A version with nothing stored is
   * {@link ClassificationStatus#UNCLASSIFIED}, which is treated like red (D-4).
   *
   * @return one entry per distinct version asked for, in the order asked
   */
  Map<ArticleVersion, Classification> forVersions(Collection<ArticleVersion> versions);
}
