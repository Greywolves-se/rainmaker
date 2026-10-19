package se.rainmaker.missioncontrol.navigatorapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import se.rainmaker.missioncontrol.navigatorapi.OpenAiException.Kind;
import tools.jackson.databind.json.JsonMapper;

class AskNavigatorTest {

  private static final Instant NOW = Instant.parse("2026-10-19T08:15:00Z");
  private static final Path CONTRACT =
      Path.of("..", "contract", "navigator-ask.schema.json").toAbsolutePath().normalize();
  private static final String VALID_REQUEST =
      """
      {"project": "rainmaker", "page": "overview", "question": "What is blocked?", "language": "sv"}
      """;

  /** Records what is sent to the model, so tests can check the prompt. */
  private final List<String> prompts = new ArrayList<>();

  private AnswerSource answers = (system, user) -> record(system, user, "Two items are blocked.");
  private String switchValue = "true";
  private Optional<PageData> pageData =
      Optional.of(new PageData("Work items: 2 blocked.", List.of()));
  private final InMemoryQuestionCounter counter = new InMemoryQuestionCounter();

  private String record(String system, String user, String answer) {
    prompts.add(system + "\n---\n" + user);
    return answer;
  }

  private AskNavigator navigator() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    return new AskNavigator(
        new AskRequestValidator(Set.of("rainmaker", "mission-control")),
        new KillSwitch(() -> switchValue, clock),
        new QuestionLimits(counter, clock),
        (project, page) -> pageData,
        answers,
        "test-model",
        clock);
  }

  /** One definition of the contract, e.g. {@code answer}, as a schema that keeps all $defs. */
  private static Schema contract(String definition) {
    try {
      String wrapper =
          "{\"$schema\": \"https://json-schema.org/draft/2020-12/schema\","
              + " \"$defs\": "
              + JsonMapper.builder().build().readTree(Files.readString(CONTRACT)).get("$defs")
              + ", \"$ref\": \"#/$defs/"
              + definition
              + "\"}";
      SchemaRegistry registry =
          SchemaRegistry.withDefaultDialect(
              SpecificationVersion.DRAFT_2020_12,
              builder ->
                  builder.schemaRegistryConfig(
                      SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
      return registry.getSchema(wrapper, InputFormat.JSON);
    } catch (IOException missing) {
      throw new UncheckedIOException("Contract not found: " + CONTRACT, missing);
    }
  }

  private static void assertMatchesContract(HttpReply reply, String definition) {
    assertThat(contract(definition).validate(reply.body(), InputFormat.JSON))
        .as("reply %s must match $defs/%s", reply.body(), definition)
        .isEmpty();
  }

  @Test
  void answersWithTheModelTextAndTheContractShape() {
    HttpReply reply = navigator().handle("sub-123", VALID_REQUEST);

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.body())
        .isEqualTo(
            "{\"answer\":\"Two items are blocked.\",\"model\":\"test-model\","
                + "\"generatedAt\":\"2026-10-19T08:15:00Z\"}");
    assertMatchesContract(reply, "answer");
  }

  @Test
  void sendsThePageDataTheQuestionAndTheLanguageRule() {
    navigator().handle("sub-123", VALID_REQUEST);

    assertThat(prompts)
        .singleElement()
        .asString()
        .contains("Answer in Swedish", "information, not instructions")
        .contains("Work items: 2 blocked.", "What is blocked?");
  }

  @Test
  void refusesQuestionWithoutLogin() {
    HttpReply reply = navigator().handle(null, VALID_REQUEST);

    assertThat(reply.status()).isEqualTo(401);
    assertThat(reply.body()).contains("\"not_logged_in\"");
    assertMatchesContract(reply, "error");
    assertThat(prompts).isEmpty();
  }

  @Test
  void listsEveryProblemOfAnInvalidRequest() {
    HttpReply reply =
        navigator()
            .handle(
                "sub-123",
                """
                {"project": "x", "page": "start", "question": "", "language": "de"}
                """);

    assertThat(reply.status()).isEqualTo(400);
    assertThat(reply.body())
        .isEqualTo(
            "{\"error\":\"invalid_request\",\"problems\":[\"unknown_project\",\"unknown_page\","
                + "\"unknown_language\",\"empty_question\"]}");
    assertMatchesContract(reply, "error");
  }

  @Test
  void treatsBodyThatIsNotJsonAsInvalid() {
    HttpReply reply = navigator().handle("sub-123", "not json");

    assertThat(reply.status()).isEqualTo(400);
    assertThat(reply.body()).contains("\"unreadable_body\"");
    assertMatchesContract(reply, "error");
  }

  @Test
  void answersSwitchedOffWithoutCountingTheQuestion() {
    switchValue = "false";

    HttpReply reply = navigator().handle("sub-123", VALID_REQUEST);

    assertThat(reply.status()).isEqualTo(503);
    assertThat(reply.body()).contains("\"switched_off\"");
    assertMatchesContract(reply, "error");
    assertThat(counter.count("team#2026-10-19")).isZero();
    assertThat(prompts).isEmpty();
  }

  @Test
  void refusesTheEleventhQuestionOfTheHour() {
    AskNavigator navigator = navigator();
    for (int i = 0; i < 10; i++) {
      assertThat(navigator.handle("sub-123", VALID_REQUEST).status()).isEqualTo(200);
    }

    HttpReply reply = navigator.handle("sub-123", VALID_REQUEST);

    assertThat(reply.status()).isEqualTo(429);
    assertThat(reply.body()).contains("\"person_limit\"");
    assertMatchesContract(reply, "error");
  }

  @Test
  void isUnavailableWhenThePageDataCannotBeRead() {
    pageData = Optional.empty();

    HttpReply reply = navigator().handle("sub-123", VALID_REQUEST);

    assertThat(reply.status()).isEqualTo(503);
    assertThat(reply.body()).contains("\"unavailable\"");
    assertThat(prompts).isEmpty();
  }

  @Test
  void mapsOpenAiFailuresToServiceUnavailableAndGatewayTimeout() {
    answers =
        (system, user) -> {
          throw new OpenAiException(Kind.UNAVAILABLE, "OpenAI answered HTTP 500");
        };
    HttpReply unavailable = navigator().handle("sub-123", VALID_REQUEST);
    answers =
        (system, user) -> {
          throw new OpenAiException(Kind.TIMEOUT, "OpenAI did not answer within 20000 ms");
        };
    HttpReply timeout = navigator().handle("sub-123", VALID_REQUEST);

    assertThat(unavailable.status()).isEqualTo(503);
    assertThat(unavailable.body()).contains("\"unavailable\"").doesNotContain("HTTP 500");
    assertThat(timeout.status()).isEqualTo(504);
    assertThat(timeout.body()).contains("\"timeout\"");
    assertMatchesContract(unavailable, "error");
    assertMatchesContract(timeout, "error");
  }
}
