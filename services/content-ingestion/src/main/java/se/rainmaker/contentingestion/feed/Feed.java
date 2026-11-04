package se.rainmaker.contentingestion.feed;

import java.util.List;

/**
 * The publisher's feed: its language and the articles in it.
 *
 * @param language the channel's {@code <language>}, e.g. {@code sv}
 */
public record Feed(String language, List<FeedItem> items) {

  /** Copies the list, so the feed cannot change afterwards. */
  public Feed {
    items = List.copyOf(items);
  }
}
