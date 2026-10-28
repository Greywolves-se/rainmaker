package se.rainmaker.trackingclick.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Signed click links as contract section 7.5 describes them (#153, D-6). */
class SignedClickLinksTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
  private static final String BASE_URL = "http://localhost:8081";
  private static final Duration LIFETIME = Duration.ofHours(24);
  private static final UUID ARTICLE = UUID.fromString("0192f3c2-0000-7000-8000-000000000001");
  private static final UUID OFFER = UUID.fromString("0192f3d0-0000-7000-8000-000000000002");
  private static final String DESTINATION = "http://localhost:8083/landing/" + OFFER;
  private static final String KEY = "the-signing-key-used-in-the-tests!";
  private static final String BASE64URL =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

  private TestClock clock;
  private SignedClickLinks links;

  @BeforeEach
  void setUp() {
    clock = new TestClock(NOW);
    links = clickLinks(key(KEY));
  }

  @Test
  void makesLinkOnTheConfiguredAddress() {
    String link = links.create(ARTICLE, OFFER, DESTINATION);

    assertThat(link).startsWith(BASE_URL + "/c/");
    assertThat(token(link)).containsOnlyOnce(".");
  }

  @Test
  void payloadHasTheContractsFields() {
    JsonNode payload = payload(links.create(ARTICLE, OFFER, DESTINATION));

    List<String> fields = new ArrayList<>();
    payload.propertyNames().forEach(fields::add);
    assertThat(fields).containsExactly("c", "a", "o", "d", "exp");
    assertThat(payload.get("a").asString()).isEqualTo(ARTICLE.toString());
    assertThat(payload.get("o").asString()).isEqualTo(OFFER.toString());
    assertThat(payload.get("d").asString()).isEqualTo(DESTINATION);
    assertThat(payload.get("exp").asLong()).isEqualTo(NOW.plus(LIFETIME).getEpochSecond());
    assertThat(UUID.fromString(payload.get("c").asString()).version()).isEqualTo(7);
  }

  @Test
  void readsBackWhatItMade() {
    String link = links.create(ARTICLE, OFFER, DESTINATION);

    ClickLinkCheck check = links.check(token(link));

    assertThat(check).isInstanceOf(ClickLinkCheck.Valid.class);
    ClickLink read = ((ClickLinkCheck.Valid) check).link();
    assertThat(read.clickId().toString()).isEqualTo(payload(link).get("c").asString());
    assertThat(read.articleId()).isEqualTo(ARTICLE);
    assertThat(read.offerId()).isEqualTo(OFFER);
    assertThat(read.destinationUrl()).isEqualTo(DESTINATION);
    assertThat(read.expiresAt()).isEqualTo(NOW.plus(LIFETIME));
  }

  @Test
  void everyLinkGetsItsOwnClickId() {
    String first = links.create(ARTICLE, OFFER, DESTINATION);
    String second = links.create(ARTICLE, OFFER, DESTINATION);

    assertThat(payload(first).get("c")).isNotEqualTo(payload(second).get("c"));
  }

  @Test
  void refusesLinkWhoseDestinationWasChanged() {
    String token = token(links.create(ARTICLE, OFFER, DESTINATION));
    ObjectNode changed = (ObjectNode) payload(BASE_URL + "/c/" + token);
    changed.put("d", "https://attacker.example/");
    String forged = encode(JSON.writeValueAsBytes(changed)) + token.substring(token.indexOf('.'));

    assertThat(links.check(forged)).isInstanceOf(ClickLinkCheck.Invalid.class);
  }

  @Test
  void refusesLinkWithChangedSignature() {
    String token = token(links.create(ARTICLE, OFFER, DESTINATION));
    String payloadPart = token.substring(0, token.indexOf('.'));

    assertThat(links.check(payloadPart + "." + encode(new byte[32])))
        .isInstanceOf(ClickLinkCheck.Invalid.class);
  }

  @Test
  void refusesLinkSignedWithAnotherKey() {
    String foreign =
        token(
            clickLinks(key("another-key-that-is-long-enough!!"))
                .create(ARTICLE, OFFER, DESTINATION));

    assertThat(links.check(foreign)).isInstanceOf(ClickLinkCheck.Invalid.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {".", "abc", "abc.", ".abc", "a.b.c", "!!!.???", "e30.e30"})
  void refusesGarbledToken(String token) {
    assertThat(links.check(token)).isInstanceOf(ClickLinkCheck.Invalid.class);
  }

  /**
   * One link has exactly one valid token: a padded signature, or one whose last character differs
   * only in the unused bits, decodes to the same bytes but is refused (#189).
   */
  @Test
  void refusesSignatureThatIsNotWrittenExactlyAsWeWriteIt() {
    String token = token(links.create(ARTICLE, OFFER, DESTINATION));
    char last = token.charAt(token.length() - 1);
    String otherUnusedBits =
        token.substring(0, token.length() - 1) + BASE64URL.charAt(BASE64URL.indexOf(last) ^ 1);

    assertThat(links.check(token + "=")).isInstanceOf(ClickLinkCheck.Invalid.class);
    assertThat(links.check(otherUnusedBits)).isInstanceOf(ClickLinkCheck.Invalid.class);
  }

  /** Only reachable with our key, but it must still be Invalid, not an error page (#189). */
  @Test
  void signedLinkWithExpiryOutsideTimeRangeIsInvalid() throws Exception {
    ObjectNode payload = (ObjectNode) payload(links.create(ARTICLE, OFFER, DESTINATION));
    payload.put("exp", Long.MAX_VALUE);

    assertThat(links.check(signed(payload))).isInstanceOf(ClickLinkCheck.Invalid.class);
  }

  @Test
  void stillValidJustBeforeItExpires() {
    String token = token(links.create(ARTICLE, OFFER, DESTINATION));
    clock.advance(LIFETIME.minusSeconds(1));

    assertThat(links.check(token)).isInstanceOf(ClickLinkCheck.Valid.class);
  }

  @Test
  void expiresAfterItsLifetime() {
    String token = token(links.create(ARTICLE, OFFER, DESTINATION));
    clock.advance(LIFETIME);

    assertThat(links.check(token)).isInstanceOf(ClickLinkCheck.Expired.class);
  }

  @Test
  void forgedLinkIsInvalidEvenWhenExpired() {
    String token = token(links.create(ARTICLE, OFFER, DESTINATION));
    String payloadPart = token.substring(0, token.indexOf('.'));
    clock.advance(LIFETIME.multipliedBy(2));

    assertThat(links.check(payloadPart + "." + encode(new byte[32])))
        .isInstanceOf(ClickLinkCheck.Invalid.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"javascript:alert(1)", "/landing/1", "ftp://localhost/file", "not a url"})
  void refusesToMakeLinkToAnythingButHttp(String destination) {
    assertThatThrownBy(() -> links.create(ARTICLE, OFFER, destination))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void makesLinkToDestinationWithUpperCaseScheme() {
    String link = links.create(ARTICLE, OFFER, "HTTPS://shop.example.se/landing");

    assertThat(links.check(token(link))).isInstanceOf(ClickLinkCheck.Valid.class);
  }

  /**
   * The redirect sends the destination as it is (part 2), so it must already be a valid URI: an
   * advertiser's macro such as {@code {clickid}} has to be percent-encoded when the offer is
   * stored.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {"https://shop.example.se/?aff={clickid}", "https://shop.example.se/?q=a b"})
  void refusesDestinationThatIsNotValidUri(String destination) {
    assertThatThrownBy(() -> links.create(ARTICLE, OFFER, destination))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private SignedClickLinks clickLinks(byte[] key) {
    return new SignedClickLinks(
        BASE_URL, LIFETIME, key, clock, new UuidV7(clock, new SecureRandom()));
  }

  private static byte[] key(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static String token(String link) {
    return link.substring(link.indexOf("/c/") + "/c/".length());
  }

  private static JsonNode payload(String link) {
    String token = token(link);
    byte[] json = Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.')));
    return JSON.readTree(json);
  }

  /** A token signed with the tests' key, for payloads {@code create} would never write. */
  private static String signed(JsonNode payload) throws Exception {
    String encoded = encode(JSON.writeValueAsBytes(payload));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key(KEY), "HmacSHA256"));
    return encoded + "." + encode(mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII)));
  }

  private static String encode(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
