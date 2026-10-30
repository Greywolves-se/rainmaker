package se.rainmaker.widgetdelivery.links;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Turns on S1's settings for G3's approved links API, {@code approved-links.*} (#151). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApprovedLinksProperties.class)
class ApprovedLinksConfiguration {}
