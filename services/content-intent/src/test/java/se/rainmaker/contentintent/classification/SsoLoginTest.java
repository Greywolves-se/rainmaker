package se.rainmaker.contentintent.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;

/**
 * Locally, G2 gets its AWS credentials from the developer's SSO login ({@code AWS_PROFILE} in
 * Bedrock mode, #161). The SDK can only read an SSO profile with its {@code sso} and {@code
 * ssooidc} modules on the classpath; without them every classification failed before any call
 * (#266).
 */
class SsoLoginTest {

  @Test
  void readsSsoProfileWithoutClasspathError(@TempDir Path folder) throws IOException {
    // A session name nobody has logged in with, so no cached token exists and no call is made.
    String session = "rainmaker-test-" + UUID.randomUUID();
    Path config = folder.resolve("config");
    Files.writeString(
        config,
        String.join(
            "\n",
            "[profile rainmaker-test]",
            "sso_session = " + session,
            "sso_account_id = 111111111111",
            "sso_role_name = RainmakerTest",
            "region = eu-north-1",
            "[sso-session " + session + "]",
            "sso_start_url = https://example.awsapps.com/start",
            "sso_region = eu-north-1",
            "sso_registration_scopes = sso:account:access",
            ""));
    ProfileFile profiles =
        ProfileFile.builder().content(config).type(ProfileFile.Type.CONFIGURATION).build();

    Throwable error =
        catchThrowable(
            () ->
                ProfileCredentialsProvider.builder()
                    .profileFile(profiles)
                    .profileName("rainmaker-test")
                    .build()
                    .resolveCredentials());

    // Without a login it still fails, but on the missing token, not on a missing SDK module.
    assertThat(error).isNotNull();
    assertThat(error).hasStackTraceContaining("token").hasMessageNotContaining("class path");
  }
}
