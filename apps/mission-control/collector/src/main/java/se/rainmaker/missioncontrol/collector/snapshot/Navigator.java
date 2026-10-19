package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;
import java.util.List;

/**
 * The Navigator's AI text per page, in Swedish and English (#115, ADR 0002). Written by an AI
 * model, so the web app shows it as plain text and labels it as AI.
 *
 * @param generatedAt when the text was generated
 * @param model the AI model that wrote it, e.g. {@code gpt-6-luna}
 * @param pages one entry per dashboard page
 */
public record Navigator(Instant generatedAt, String model, Pages pages) {

  /**
   * The text per page; a page is null when its text could not be generated.
   *
   * @param overview the Overview page
   * @param development the Development page
   * @param sources the Sources page
   */
  public record Pages(PageInsight overview, PageInsight development, PageInsight sources) {}

  /**
   * The same insight in both languages, from one answer.
   *
   * @param sv Swedish
   * @param en English
   */
  public record PageInsight(Insight sv, Insight en) {}

  /**
   * A summary of the page, what deviates from the plan, and suggested next steps.
   *
   * @param summary a short paragraph
   * @param deviations what is late, blocked, overdue, red or failing compared with the plan
   * @param suggestions suggested next steps
   */
  public record Insight(String summary, List<String> deviations, List<String> suggestions) {

    /** Copies the lists, so an insight cannot change after it is built. */
    public Insight {
      deviations = List.copyOf(deviations);
      suggestions = List.copyOf(suggestions);
    }
  }
}
