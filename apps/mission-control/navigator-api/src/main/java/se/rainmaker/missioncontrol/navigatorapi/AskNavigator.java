package se.rainmaker.missioncontrol.navigatorapi;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import se.rainmaker.missioncontrol.navigatorapi.QuestionLimits.Result;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code POST /api/navigator/ask} (ADR 0003): checks the request step by step and replies with the
 * contract in {@code contract/navigator-ask.schema.json}. The order is login (401), validation
 * (400), kill switch (503), limits (429), page data (503) and the model (200, 503 or 504). A
 * question refused before the limits is not counted.
 */
public final class AskNavigator {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final AskRequestValidator validator;
  private final KillSwitch killSwitch;
  private final QuestionLimits limits;
  private final PageDataSource pages;
  private final AnswerSource answers;
  private final String model;
  private final Clock clock;

  /** Wires the steps together; every part can be replaced in tests. */
  public AskNavigator(
      AskRequestValidator validator,
      KillSwitch killSwitch,
      QuestionLimits limits,
      PageDataSource pages,
      AnswerSource answers,
      String model,
      Clock clock) {
    this.validator = Objects.requireNonNull(validator);
    this.killSwitch = Objects.requireNonNull(killSwitch);
    this.limits = Objects.requireNonNull(limits);
    this.pages = Objects.requireNonNull(pages);
    this.answers = Objects.requireNonNull(answers);
    this.model = Objects.requireNonNull(model);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Answers one question.
   *
   * @param loginId the verified login {@code sub} id, or {@code null} when there is none
   * @param requestJson the request body as sent by the web app
   */
  public HttpReply handle(String loginId, String requestJson) {
    if (loginId == null || loginId.isBlank()) {
      return error(401, "not_logged_in", List.of());
    }
    Optional<AskRequest> request = parse(requestJson);
    if (request.isEmpty()) {
      return error(400, "invalid_request", List.of("unreadable_body"));
    }
    List<AskRequestValidator.Problem> problems = validator.problems(request.get());
    if (!problems.isEmpty()) {
      return error(
          400,
          "invalid_request",
          problems.stream().map(problem -> problem.name().toLowerCase(Locale.ROOT)).toList());
    }
    if (!killSwitch.isOn()) {
      return error(503, "switched_off", List.of());
    }
    Result allowed = limits.tryAsk(loginId);
    if (allowed == Result.PERSON_LIMIT_REACHED) {
      return error(429, "person_limit", List.of());
    }
    if (allowed == Result.TEAM_LIMIT_REACHED) {
      return error(429, "team_limit", List.of());
    }
    return answer(request.get());
  }

  private HttpReply answer(AskRequest request) {
    Optional<String> pageData = pages.pageData(request.project(), request.page());
    if (pageData.isEmpty()) {
      return error(503, "unavailable", List.of());
    }
    // Before merge (#115): anonymise the page data and the question with the shared anonymizer
    // module (names from the snapshot) and restore the names in the answer. Until then only fake
    // data is used: this code is not merged before ADR 0002 and 0003 are approved.
    try {
      String text =
          answers.complete(
              NavigatorPrompt.system(request.language()),
              NavigatorPrompt.user(pageData.get(), request.question()));
      ObjectNode body = JSON.createObjectNode();
      body.put("answer", text);
      body.put("model", model);
      body.put("generatedAt", clock.instant().toString());
      return new HttpReply(200, JSON.writeValueAsString(body));
    } catch (OpenAiException failed) {
      return failed.kind() == OpenAiException.Kind.TIMEOUT
          ? error(504, "timeout", List.of())
          : error(503, "unavailable", List.of());
    }
  }

  /** Reads the four fields; a field of the wrong type becomes null, which fails validation. */
  private static Optional<AskRequest> parse(String requestJson) {
    try {
      JsonNode node = JSON.readTree(requestJson == null ? "" : requestJson);
      if (node == null || !node.isObject()) {
        return Optional.empty();
      }
      return Optional.of(
          new AskRequest(
              text(node, "project"),
              text(node, "page"),
              text(node, "question"),
              text(node, "language")));
    } catch (JacksonException unreadable) {
      return Optional.empty();
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }

  private static HttpReply error(int status, String code, List<String> problems) {
    ObjectNode body = JSON.createObjectNode();
    body.put("error", code);
    ArrayNode list = body.putArray("problems");
    problems.forEach(list::add);
    return new HttpReply(status, JSON.writeValueAsString(body));
  }
}
