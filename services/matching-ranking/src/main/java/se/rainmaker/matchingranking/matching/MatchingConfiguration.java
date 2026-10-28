package se.rainmaker.matchingranking.matching;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import se.rainmaker.matchingranking.offers.OffersProperties;

/** Turns on matching's settings, {@code rainmaker.matching-ranking.offers.*} (#149). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OffersProperties.class)
class MatchingConfiguration {}
