package se.rainmaker.trackingclick.click;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The one rule for addresses in click links (#153): G1's own address and every destination must be
 * a valid absolute http(s) URI with a host. The redirect sends the destination as it is (part 2),
 * so an address that isn't a valid URI is refused here, before any link to it is made.
 */
final class HttpAddresses {

  private HttpAddresses() {}

  /**
   * Checks one address. The scheme may be in any case ({@code HTTPS://} too).
   *
   * @param address the address to check
   * @param name what the address is, for the message, e.g. {@code The destination}
   * @throws IllegalArgumentException if it is missing, not a valid URI, not http(s) or has no host
   */
  static void requireAbsoluteHttp(String address, String name) {
    if (address == null || address.isBlank()) {
      throw new IllegalArgumentException(name + " is missing");
    }
    URI uri;
    try {
      uri = new URI(address);
    } catch (URISyntaxException notUri) {
      throw new IllegalArgumentException(name + " is not a valid address", notUri);
    }
    String scheme = uri.getScheme();
    boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    if (!http || uri.getHost() == null) {
      throw new IllegalArgumentException(name + " must be an absolute http(s) address");
    }
  }
}
