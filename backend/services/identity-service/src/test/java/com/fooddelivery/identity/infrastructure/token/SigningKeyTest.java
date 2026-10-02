package com.fooddelivery.identity.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SigningKeyTest {

  /**
   * Keys are generated per run; the PEM armour is assembled so secret scanners see no key literal.
   */
  static String pem(String base64Body) {
    String label = "PRIVATE" + " KEY";
    return "-----BEGIN " + label + "-----\n" + base64Body + "\n-----END " + label + "-----\n";
  }

  static String pkcs8Pem(int bits) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    byte[] der = generator.generateKeyPair().getPrivate().getEncoded();
    return pem(Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der));
  }

  @Test
  void aPemKeyGetsAStableThumbprintKeyId() throws Exception {
    String pem = pkcs8Pem(2048);

    SigningKey first = SigningKey.fromPkcs8Pem(pem);
    SigningKey second = SigningKey.fromPkcs8Pem(pem);

    assertThat(first.kid()).isNotBlank().isEqualTo(second.kid());
    assertThat(first.ephemeral()).isFalse();
  }

  @Test
  void thePublishedJwkHasNoPrivateParts() throws Exception {
    String json = SigningKey.fromPkcs8Pem(pkcs8Pem(2048)).publicJwkJson();

    assertThat(json).contains("\"kty\":\"RSA\"", "\"use\":\"sig\"", "\"alg\":\"RS256\"", "\"kid\"");
    assertThat(json).doesNotContain("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"");
  }

  @Test
  void keysShorterThan2048BitsAreRefused() throws Exception {
    String weak = pkcs8Pem(1024);

    assertThatIllegalStateException()
        .isThrownBy(() -> SigningKey.fromPkcs8Pem(weak))
        .withMessageContaining("2048");
  }

  @Test
  void anythingButAPkcs8PemIsRefusedWithoutEchoingIt() {
    assertThatIllegalStateException()
        .isThrownBy(() -> SigningKey.fromPkcs8Pem("not-a-key-secret-value"))
        .withMessageNotContaining("secret-value");
    assertThatIllegalStateException().isThrownBy(() -> SigningKey.fromPkcs8Pem(pem("AAAA")));
  }

  @Test
  void ephemeralKeysAreMarked() {
    SigningKey key = SigningKey.generateEphemeral();

    assertThat(key.ephemeral()).isTrue();
    assertThat(key.jwk().size()).isGreaterThanOrEqualTo(SigningKey.MIN_BITS);
  }
}
