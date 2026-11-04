package se.rainmaker.g3publisherapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * G3 Publisher & marketplace: assembles S3 Publisher Control and S4 Advertiser Marketplace (#145,
 * #155). Only the listed service packages are scanned, so the app states exactly what it runs.
 */
@SpringBootApplication(
    scanBasePackages = {
      "se.rainmaker.g3publisherapp",
      "se.rainmaker.publishercontrol",
      "se.rainmaker.advertisermarketplace"
    })
public class G3PublisherApplication {

  /** Starts the app. */
  public static void main(String[] args) {
    SpringApplication.run(G3PublisherApplication.class, args);
  }
}
