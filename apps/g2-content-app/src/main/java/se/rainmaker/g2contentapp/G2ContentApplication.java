package se.rainmaker.g2contentapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * G2 Content & intelligence: assembles S5 Content Ingestion, S6 Content & Intent and S7 Matching &
 * Ranking (#145, #155). Only the listed service packages are scanned, so the app states exactly
 * what it runs.
 */
@SpringBootApplication(
    scanBasePackages = {
      "se.rainmaker.g2contentapp",
      "se.rainmaker.contentingestion",
      "se.rainmaker.contentintent",
      "se.rainmaker.matchingranking"
    })
public class G2ContentApplication {

  /** Starts the app. */
  public static void main(String[] args) {
    SpringApplication.run(G2ContentApplication.class, args);
  }
}
