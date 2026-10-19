package se.rainmaker.missioncontrol.collector;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.rainmaker.missioncontrol.collector.datafiles.DataFilesProperties;
import se.rainmaker.missioncontrol.collector.drive.GoogleDriveProperties;
import se.rainmaker.missioncontrol.collector.drive.GoogleLogin;
import se.rainmaker.missioncontrol.collector.github.GithubProperties;
import se.rainmaker.missioncontrol.collector.navigator.NavigatorProperties;
import se.rainmaker.missioncontrol.collector.pipeline.CollectorProperties;

/** Beans the collector needs besides its components. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  CollectorProperties.class,
  DataFilesProperties.class,
  GithubProperties.class,
  GoogleDriveProperties.class,
  NavigatorProperties.class
})
class CollectorConfiguration {

  /** UTC clock for timestamps; tests replace it with a fixed one. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Google's standard login for the Drive source (#120): Workload Identity Federation in CI, gcloud
   * locally. No key is configured anywhere.
   */
  @Bean
  GoogleLogin googleLogin() {
    return GoogleLogin.standard();
  }
}
