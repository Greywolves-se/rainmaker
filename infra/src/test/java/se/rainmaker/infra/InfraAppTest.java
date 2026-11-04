package se.rainmaker.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

/**
 * Which stacks the app defines, and in which account and region; both come from cdk.json context.
 */
class InfraAppTest {

  private static Map<String, Object> context() {
    Map<String, Object> context = new HashMap<>();
    context.put("rainmaker:staging", Map.of("account", "659947636280", "region", "eu-north-1"));
    context.put("rainmaker:dev", Map.of("account", "111111111111", "region", "eu-north-1"));
    context.put("rainmaker:bedrock", BedrockModelAccessTest.bedrockContext());
    return context;
  }

  private static App defineStacks(Map<String, Object> context) {
    App app = App.Builder.create().context(context).build();
    InfraApp.defineStacks(app);
    return app;
  }

  private static Stack stack(App app, String id) {
    return (Stack) app.getNode().findChild(id);
  }

  @Test
  void keepsTheStagingStackInTheStagingAccount() {
    Stack staging = stack(defineStacks(context()), "GithubDeployAccess-staging");

    assertThat(staging.getAccount()).isEqualTo("659947636280");
    assertThat(staging.getRegion()).isEqualTo("eu-north-1");
  }

  @Test
  void putsTheBedrockStackInTheDevAccountFromCdkJson() {
    Stack bedrock = stack(defineStacks(context()), "BedrockAccess-dev");

    assertThat(bedrock.getAccount()).isEqualTo("111111111111");
    assertThat(bedrock.getRegion()).isEqualTo("eu-north-1");
  }

  @Test
  void refusesDevContextWithoutAccount() {
    Map<String, Object> context = context();
    context.put("rainmaker:dev", Map.of("region", "eu-north-1"));

    assertThatThrownBy(() -> defineStacks(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rainmaker:dev")
        .hasMessageContaining("account");
  }

  @Test
  void refusesDevAccountThatIsNoAccountId() {
    Map<String, Object> context = context();
    context.put("rainmaker:dev", Map.of("account", "greywolves", "region", "eu-north-1"));

    assertThatThrownBy(() -> defineStacks(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rainmaker:dev")
        .hasMessageContaining("12-digit");
  }

  /**
   * The Bedrock calls are made in the dev region, so it must be in the EU like the destinations.
   */
  @Test
  void refusesDevRegionOutsideTheEu() {
    Map<String, Object> context = context();
    context.put("rainmaker:dev", Map.of("account", "111111111111", "region", "us-east-1"));

    assertThatThrownBy(() -> defineStacks(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rainmaker:dev")
        .hasMessageContaining("us-east-1");
  }
}
