package se.rainmaker.missioncontrol.navigatorapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks a question against fixed lists before anything else happens (ADR 0003), so nothing in the
 * request can choose another snapshot file or prompt. The problems are codes, never the input, so
 * they are safe to return to the reader and to log.
 */
public final class AskRequestValidator {

  /** Longest question, counted in characters as the reader sees them (an emoji is one). */
  static final int MAX_QUESTION_LENGTH = 500;

  private static final Set<String> PAGES = Set.of("overview", "development", "sources");
  private static final Set<String> LANGUAGES = Set.of("sv", "en");

  /** What is wrong with a request, in the order the fields are checked. */
  public enum Problem {
    UNKNOWN_PROJECT,
    UNKNOWN_PAGE,
    UNKNOWN_LANGUAGE,
    EMPTY_QUESTION,
    QUESTION_TOO_LONG
  }

  private final Set<String> knownProjects;

  /**
   * Creates a validator for the projects that have a snapshot.
   *
   * @param knownProjects the project ids from {@code projects.json}
   */
  public AskRequestValidator(Set<String> knownProjects) {
    this.knownProjects = Set.copyOf(knownProjects);
  }

  /** Returns every problem with the request; an empty list means it is valid. */
  public List<Problem> problems(AskRequest request) {
    List<Problem> problems = new ArrayList<>();
    if (request.project() == null || !knownProjects.contains(request.project())) {
      problems.add(Problem.UNKNOWN_PROJECT);
    }
    if (request.page() == null || !PAGES.contains(request.page())) {
      problems.add(Problem.UNKNOWN_PAGE);
    }
    if (request.language() == null || !LANGUAGES.contains(request.language())) {
      problems.add(Problem.UNKNOWN_LANGUAGE);
    }
    String question = request.question();
    if (question == null || question.isBlank()) {
      problems.add(Problem.EMPTY_QUESTION);
    } else if (question.codePointCount(0, question.length()) > MAX_QUESTION_LENGTH) {
      problems.add(Problem.QUESTION_TOO_LONG);
    }
    return List.copyOf(problems);
  }
}
