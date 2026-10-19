package se.rainmaker.missioncontrol.navigatorapi;

/**
 * A reader's question about one page of one project (ADR 0003). Any field may be missing in the
 * JSON, so all of them may be {@code null}; {@link AskRequestValidator} decides what is valid.
 *
 * @param project the project id, which must be in {@code projects.json}
 * @param page {@code overview}, {@code development} or {@code sources}
 * @param question the free-text question, at most 500 characters
 * @param language {@code sv} or {@code en}, the language of the answer
 */
public record AskRequest(String project, String page, String question, String language) {}
