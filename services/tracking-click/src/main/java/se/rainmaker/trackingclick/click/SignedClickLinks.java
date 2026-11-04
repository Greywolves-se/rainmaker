package se.rainmaker.trackingclick.click;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import se.rainmaker.trackingclick.api.ClickLinks;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Click links signed with HMAC-SHA256 (#153, contract 7.5, D-6): {@code
 * <base-url>/c/<payload>.<signature>}, where the payload is base64url JSON with the click id
 * ({@code c}), article ({@code a}), offer ({@code o}), destination ({@code d}) and expiry ({@code
 * exp}, Unix seconds), and the signature is base64url HMAC-SHA256 of the payload part.
 *
 * <p>The destination travels inside the signed link, so the redirect needs no other app and can
 * never be pointed somewhere else by changing the link (no open redirect).
 */
final class SignedClickLinks implements ClickLinks {

  /** The click links' address, before the token. */
  static final String PATH_PREFIX = "/c/";

  private static final String HMAC_SHA_256 = "HmacSHA256";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private static final String DESTINATION = "The destination";

  private final String baseUrl;
  private final Duration lifetime;
  private final SecretKeySpec key;
  private final Clock clock;
  private final UuidV7 clickIds;

  SignedClickLinks(
      String baseUrl, Duration lifetime, byte[] signingKey, Clock clock, UuidV7 clickIds) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.lifetime = lifetime;
    this.key = new SecretKeySpec(signingKey.clone(), HMAC_SHA_256);
    this.clock = clock;
    this.clickIds = clickIds;
  }

  @Override
  public String create(UUID articleId, UUID offerId, String destinationUrl) {
    Objects.requireNonNull(articleId, "articleId");
    Objects.requireNonNull(offerId, "offerId");
    HttpAddresses.requireAbsoluteHttp(destinationUrl, DESTINATION);
    ObjectNode payload = JSON.createObjectNode();
    payload.put("c", clickIds.next().toString());
    payload.put("a", articleId.toString());
    payload.put("o", offerId.toString());
    payload.put("d", destinationUrl);
    payload.put("exp", clock.instant().plus(lifetime).getEpochSecond());
    String encoded = ENCODER.encodeToString(JSON.writeValueAsBytes(payload));
    return baseUrl + PATH_PREFIX + encoded + "." + ENCODER.encodeToString(sign(encoded));
  }

  /**
   * Checks a token (the part after {@code /c/}). The signature is checked first, so nothing from an
   * unsigned or changed token is ever read; only then the contents and the expiry.
   *
   * <p>The signature must be written exactly as {@link #create} writes it. A decoder would also
   * accept padding, or other values in the last character's unused bits, which would give one link
   * several valid tokens.
   */
  ClickLinkCheck check(String token) {
    if (token == null) {
      return new ClickLinkCheck.Invalid();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) {
      return new ClickLinkCheck.Invalid();
    }
    String encoded = token.substring(0, dot);
    ClickLink link;
    try {
      byte[] expected = ENCODER.encodeToString(sign(encoded)).getBytes(StandardCharsets.US_ASCII);
      byte[] signature = token.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
      if (!MessageDigest.isEqual(expected, signature)) {
        return new ClickLinkCheck.Invalid();
      }
      link = read(DECODER.decode(encoded));
    } catch (IllegalArgumentException | JacksonException | DateTimeException e) {
      // Not base64url, or a payload we didn't write (an expiry outside Instant's range too): the
      // same answer as a forged link.
      return new ClickLinkCheck.Invalid();
    }
    if (!clock.instant().isBefore(link.expiresAt())) {
      return new ClickLinkCheck.Expired();
    }
    return new ClickLinkCheck.Valid(link);
  }

  private static ClickLink read(byte[] json) {
    JsonNode payload = JSON.readTree(json);
    JsonNode expiry = payload.get("exp");
    if (expiry == null || !expiry.isIntegralNumber()) {
      throw new IllegalArgumentException("exp is missing");
    }
    String destination = text(payload, "d");
    HttpAddresses.requireAbsoluteHttp(destination, DESTINATION);
    return new ClickLink(
        UUID.fromString(text(payload, "c")),
        UUID.fromString(text(payload, "a")),
        UUID.fromString(text(payload, "o")),
        destination,
        Instant.ofEpochSecond(expiry.asLong()));
  }

  private static String text(JsonNode payload, String field) {
    JsonNode value = payload.get(field);
    if (value == null || !value.isString()) {
      throw new IllegalArgumentException(field + " is missing");
    }
    return value.asString();
  }

  private byte[] sign(String encodedPayload) {
    try {
      // A Mac is not thread-safe, so each signature gets its own.
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(key);
      return mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      // Every Java runtime has HmacSHA256, and the key was checked at start-up.
      throw new IllegalStateException("Cannot sign click links", e);
    }
  }
}
