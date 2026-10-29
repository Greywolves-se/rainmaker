package se.rainmaker.widgetdelivery.links;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The JSON examples in {@code docs/rainmaker/contract/}, which both sides of a call test against
 * (contract 7, "Contract tests").
 */
final class ContractExamples {

  private ContractExamples() {}

  /** A contract example, found by walking up from the working directory (Maven, root or IDE). */
  static String read(String name) {
    Path file = Path.of("docs", "rainmaker", "contract", name);
    for (Path folder = Path.of("").toAbsolutePath(); folder != null; folder = folder.getParent()) {
      if (Files.isRegularFile(folder.resolve(file))) {
        try {
          return Files.readString(folder.resolve(file), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
          throw new IllegalStateException("Cannot read " + file, unreadable);
        }
      }
    }
    throw new IllegalStateException(
        "Cannot find " + file + " above " + Path.of("").toAbsolutePath());
  }
}
