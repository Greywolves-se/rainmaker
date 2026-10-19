package se.rainmaker.missioncontrol.navigatorapi;

import java.util.List;

/**
 * What one page shows, read from the project's snapshot (ADR 0003).
 *
 * @param text the page's data as text for the prompt
 * @param names every person name and login in the snapshot's person fields (assignees, owners,
 *     authors, last editors). They are hidden before anything is sent to OpenAI. They come from the
 *     snapshot, never from the request.
 */
public record PageData(String text, List<String> names) {

  /** Copies the names, so the list cannot change afterwards. */
  public PageData {
    names = List.copyOf(names);
  }
}
