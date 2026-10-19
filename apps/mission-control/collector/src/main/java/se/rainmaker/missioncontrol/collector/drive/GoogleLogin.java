package se.rainmaker.missioncontrol.collector.drive;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;

/**
 * Where the Drive source gets its Google credentials (#120). Production uses {@link #standard()},
 * Google's standard login (Application Default Credentials): Workload Identity Federation in CI, or
 * {@code gcloud auth application-default login} locally. No key is stored anywhere. Tests hand in
 * their own credentials instead.
 */
@FunctionalInterface
public interface GoogleLogin {

  /**
   * The credentials to log in with, not yet scoped.
   *
   * @throws IOException when no login is available
   */
  GoogleCredentials credentials() throws IOException;

  /** Google's standard login (Application Default Credentials). */
  static GoogleLogin standard() {
    return GoogleCredentials::getApplicationDefault;
  }
}
