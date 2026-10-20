package se.rainmaker.missioncontrol.collector.github;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Git Flow version {@code x.y.z}, as in a {@code release/x.y.z} branch or a {@code vx.y.z} tag
 * (#14). Versions are compared by number, so 1.10.0 is newer than 1.9.0.
 */
record ReleaseVersion(int major, int minor, int patch) implements Comparable<ReleaseVersion> {

  private static final Pattern VERSION =
      Pattern.compile("^v?(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})$");
  private static final Comparator<ReleaseVersion> ORDER =
      Comparator.comparingInt(ReleaseVersion::major)
          .thenComparingInt(ReleaseVersion::minor)
          .thenComparingInt(ReleaseVersion::patch);

  /** The version in {@code text} ({@code 1.2.0} or {@code v1.2.0}); empty for anything else. */
  static Optional<ReleaseVersion> parse(String text) {
    Matcher matcher = VERSION.matcher(text);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new ReleaseVersion(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))));
  }

  @Override
  public int compareTo(ReleaseVersion other) {
    return ORDER.compare(this, other);
  }
}
