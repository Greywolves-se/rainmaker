package se.rainmaker.missioncontrol.navigatorapi;

/** Something that answers a prompt: {@link OpenAiClient} in production, a fake in tests. */
@FunctionalInterface
public interface AnswerSource {

  /**
   * Returns the answer to one question.
   *
   * @throws OpenAiException when no answer can be given
   */
  String complete(String systemPrompt, String userMessage);
}
