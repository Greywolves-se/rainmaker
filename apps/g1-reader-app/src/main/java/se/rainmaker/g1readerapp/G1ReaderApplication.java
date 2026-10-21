package se.rainmaker.g1readerapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * G1 Reader & clicks: assembles S1 Widget Delivery and S2 Tracking & Click (#145, #155). Only the
 * listed service packages are scanned, so the app states exactly what it runs.
 */
@SpringBootApplication(
    scanBasePackages = {
      "se.rainmaker.g1readerapp",
      "se.rainmaker.widgetdelivery",
      "se.rainmaker.trackingclick"
    })
public class G1ReaderApplication {

  /** Starts the app. */
  public static void main(String[] args) {
    SpringApplication.run(G1ReaderApplication.class, args);
  }
}
