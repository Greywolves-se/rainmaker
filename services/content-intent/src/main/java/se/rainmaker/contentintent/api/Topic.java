package se.rainmaker.contentintent.api;

import java.util.Arrays;
import java.util.Optional;

/**
 * What an article is about, as a commercial category (#145, decision D-3). Offers use the same
 * values as their category. The keys are code values; display names come from the translation
 * files.
 */
public enum Topic {
  /** Travel, e.g. Mallorca in autumn. */
  TRAVEL("travel"),
  /** Outdoor life, e.g. a mountain hiking guide. */
  OUTDOOR("outdoor"),
  /** Sport, e.g. a football promotion or a running race. */
  SPORT("sport"),
  /** Culture, e.g. a theatre musical. */
  CULTURE("culture"),
  /** Food, e.g. chanterelles and a recipe. */
  FOOD("food"),
  /** Home, e.g. a bathroom renovation. */
  HOME("home"),
  /** Cars, e.g. an electric car test. */
  CARS("cars"),
  /** Health, e.g. young people sleeping too little. */
  HEALTH("health"),
  /** Personal finance, e.g. household savings. */
  PERSONAL_FINANCE("personal-finance"),
  /** Nothing commercial fits; no offer has this category, so it never matches. */
  OTHER("other");

  private final String key;

  Topic(String key) {
    this.key = key;
  }

  /** The value used in JSON and the database, e.g. {@code "personal-finance"}. */
  public String key() {
    return key;
  }

  /** The topic with this exact key, if any. */
  public static Optional<Topic> fromKey(String key) {
    return Arrays.stream(values()).filter(topic -> topic.key.equals(key)).findFirst();
  }
}
