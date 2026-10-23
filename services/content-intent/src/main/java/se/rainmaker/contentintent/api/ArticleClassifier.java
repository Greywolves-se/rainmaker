package se.rainmaker.contentintent.api;

/**
 * Classifies one article: zone, topics and reason (#148, contract section 8). The Bedrock call is
 * one implementation; a fake one is used for local work and tests.
 *
 * <p>Never throws for problems with the model or its answer, and never returns green by default:
 * those become {@link ClassificationStatus#FAILED}. When the module's own call limits defer the
 * call, the result is {@link ClassificationStatus#UNCLASSIFIED}, so it can be classified later.
 */
public interface ArticleClassifier {

  /** Classifies the article. */
  Classification classify(ArticleToClassify article);
}
