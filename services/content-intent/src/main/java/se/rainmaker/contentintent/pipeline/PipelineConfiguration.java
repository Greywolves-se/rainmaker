package se.rainmaker.contentintent.pipeline;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.rainmaker.contentintent.api.ArticleClassifier;

/** Wires the classification of stored articles (#209). */
@Configuration(proxyBeanMethods = false)
class PipelineConfiguration {

  @Bean
  ArticleClassification articleClassification(
      ClassificationStore store, ClassificationResults results, ArticleClassifier classifier) {
    return new ArticleClassification(store, results, classifier, Clock.systemUTC());
  }
}
