package se.rainmaker.missioncontrol.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Command-line app that collects project data and writes the Mission Control snapshot. */
@SpringBootApplication
public class CollectorApplication {

  /**
   * Runs the collector once and exits with Spring's exit code (0 on success), so that open
   * resources such as HTTP clients can never keep the process alive.
   */
  public static void main(String[] args) {
    System.exit(SpringApplication.exit(SpringApplication.run(CollectorApplication.class, args)));
  }
}
